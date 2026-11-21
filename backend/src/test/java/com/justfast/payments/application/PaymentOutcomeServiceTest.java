package com.justfast.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.justfast.identity.domain.Address.AddressDetails;
import com.justfast.inventory.application.StockReservationService;
import com.justfast.orders.application.OrderPaymentService;
import com.justfast.orders.domain.Order;
import com.justfast.orders.domain.OrderLine;
import com.justfast.orders.domain.OrderStatus;
import com.justfast.orders.domain.ShippingAddress;
import com.justfast.orders.infrastructure.OrderRepository;
import com.justfast.payments.application.PaymentOutcomeService.Outcome;
import com.justfast.payments.application.PaymentOutcomeService.Result;
import com.justfast.payments.domain.PaymentAttempt;
import com.justfast.payments.domain.PaymentAttemptStatus;
import com.justfast.payments.infrastructure.PaymentAttemptRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Payment outcome rules, tested without HTTP or a database. */
class PaymentOutcomeServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
    private static final Long ORDER_ID = 42L;

    private PaymentAttemptRepository attempts;
    private StockReservationService reservations;
    private PaymentOutcomeService service;
    private Order order;
    private PaymentAttempt attempt;

    @BeforeEach
    void setUp() {
        attempts = mock(PaymentAttemptRepository.class);
        reservations = mock(StockReservationService.class);
        OrderRepository orders = mock(OrderRepository.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new PaymentOutcomeService(attempts, new OrderPaymentService(orders, reservations, clock), clock);

        order = Order.place("JF-TEST", 7L, "key-12345678", "USD",
                List.of(new OrderLine(1L, "SKU", "Bottle", new BigDecimal("24.00"), 1)), new BigDecimal("5.00"),
                ShippingAddress.snapshotOf(new AddressDetails("Ada", "1 Main", null, "Town", null, "1000", "US", null)),
                NOW, NOW.plus(Duration.ofMinutes(30)));
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        attempt = new PaymentAttempt(ORDER_ID, "sandbox", new BigDecimal("29.00"), "USD", NOW);
        attempt.sessionCreated("sess_1", "https://pay", NOW);

        when(orders.lockById(ORDER_ID)).thenReturn(Optional.of(order));
        when(attempts.findOrderIdById(attempt.getId())).thenReturn(Optional.of(ORDER_ID));
        when(attempts.findById(attempt.getId())).thenReturn(Optional.of(attempt));
    }

    @Test
    void successPaysOrderAndCommitsStock() {
        Result result = service.apply(attempt.getId(), Outcome.SUCCEEDED, new BigDecimal("29.00"), "usd");

        assertThat(result).isEqualTo(Result.APPLIED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        verify(reservations).commit(ORDER_ID);
    }

    @Test
    void duplicateSuccessIsNoOp() {
        service.apply(attempt.getId(), Outcome.SUCCEEDED, new BigDecimal("29.00"), "USD");
        Result second = service.apply(attempt.getId(), Outcome.SUCCEEDED, new BigDecimal("29.00"), "USD");

        assertThat(second).isEqualTo(Result.NO_CHANGE);
        verify(reservations, times(1)).commit(ORDER_ID);
    }

    @Test
    void amountMismatchGoesToReviewWithoutCommittingStock() {
        Result result = service.apply(attempt.getId(), Outcome.SUCCEEDED, new BigDecimal("1.00"), "USD");

        assertThat(result).isEqualTo(Result.NEEDS_REVIEW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_REVIEW);
        verify(reservations, never()).commit(anyLong());
    }

    @Test
    void latePaymentOnCancelledOrderGoesToReview() {
        order.cancelUnpaid(NOW);
        Result result = service.apply(attempt.getId(), Outcome.SUCCEEDED, new BigDecimal("29.00"), "USD");

        assertThat(result).isEqualTo(Result.NEEDS_REVIEW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_REVIEW);
        verify(reservations, never()).commit(anyLong());
    }

    @Test
    void failureCancelsOrderAndReleasesStockWhenNoOtherAttemptIsOpen() {
        when(attempts.countByOrderIdAndStatusIn(any(), any())).thenReturn(0L);

        Result result = service.apply(attempt.getId(), Outcome.FAILED, null, null);

        assertThat(result).isEqualTo(Result.APPLIED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
        verify(reservations).release(ORDER_ID);
    }

    @Test
    void failureKeepsReservationWhileAnotherAttemptIsStillOpen() {
        when(attempts.countByOrderIdAndStatusIn(any(), any())).thenReturn(1L);

        service.apply(attempt.getId(), Outcome.EXPIRED, null, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(reservations, never()).release(anyLong());
    }

    @Test
    void failureAfterSuccessDoesNotUndoPayment() {
        service.apply(attempt.getId(), Outcome.SUCCEEDED, new BigDecimal("29.00"), "USD");
        Result result = service.apply(attempt.getId(), Outcome.FAILED, null, null);

        assertThat(result).isEqualTo(Result.NO_CHANGE);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(reservations, never()).release(anyLong());
    }

    @Test
    void unknownAttemptIsIgnored() {
        assertThat(service.apply("nope", Outcome.SUCCEEDED, BigDecimal.ONE, "USD")).isEqualTo(Result.UNKNOWN_ATTEMPT);
    }
}
