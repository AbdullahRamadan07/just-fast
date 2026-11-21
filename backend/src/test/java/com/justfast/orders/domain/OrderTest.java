package com.justfast.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.justfast.common.error.BusinessRuleException;
import com.justfast.identity.domain.Address.AddressDetails;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    private static Order newOrder() {
        return Order.place("JF-1", 7L, "key-12345678", "USD",
                List.of(new OrderLine(1L, "SKU-1", "Kettle", new BigDecimal("39.95"), 2)),
                new BigDecimal("5.00"),
                ShippingAddress.snapshotOf(new AddressDetails("Ada", "1 Main St", null, "Springfield", "IL", "62701", "US", null)),
                NOW, NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    void placesPendingOrderWithPurchaseTimeSnapshot() {
        Order order = newOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getSubtotal()).isEqualByComparingTo("79.90");
        assertThat(order.getTotal()).isEqualByComparingTo("84.90");
        assertThat(order.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getProductName()).isEqualTo("Kettle");
            assertThat(item.getUnitPrice()).isEqualByComparingTo("39.95");
            assertThat(item.getCurrency()).isEqualTo("USD");
        });
        assertThat(order.getShippingAddress().toDetails().city()).isEqualTo("Springfield");
    }

    @Test
    void followsFulfillmentLifecycle() {
        Order order = newOrder();
        order.markPaid(NOW);
        order.ship(NOW);
        order.deliver(NOW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void unpaidOrdersCannotShip() {
        Order order = newOrder();
        assertThatThrownBy(() -> order.ship(NOW)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cancelledOrdersCannotBePaidOrShipped() {
        Order order = newOrder();
        order.cancelUnpaid(NOW);
        assertThatThrownBy(() -> order.markPaid(NOW)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> order.ship(NOW)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void latePaymentOnCancelledOrderGoesToReviewAndCannotShip() {
        Order order = newOrder();
        order.cancelUnpaid(NOW);
        order.flagForPaymentReview(NOW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_REVIEW);
        assertThatThrownBy(() -> order.ship(NOW)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void emptyOrdersAreRejected() {
        assertThatThrownBy(() -> Order.place("JF-2", 1L, "key-12345678", "USD", List.of(), BigDecimal.ONE,
                null, NOW, NOW)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void reservationExpiry() {
        Order order = newOrder();
        assertThat(order.isReservationExpired(NOW)).isFalse();
        assertThat(order.isReservationExpired(NOW.plus(Duration.ofMinutes(30)))).isTrue();
    }
}
