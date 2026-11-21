package com.justfast.payments.application;

import com.justfast.orders.application.OrderPaymentService;
import com.justfast.orders.domain.Order;
import com.justfast.payments.domain.PaymentAttempt;
import com.justfast.payments.domain.PaymentAttemptStatus;
import com.justfast.payments.infrastructure.PaymentAttemptRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a confirmed payment outcome (from a verified webhook or from reconciliation) to the payment attempt,
 * the order, and stock — all in one database transaction, under a row lock on the order.
 *
 * <p>Rules:
 * <ul>
 *   <li>Success on an order awaiting payment with matching amount/currency: order PAID, stock committed.</li>
 *   <li>Success that arrives after the order was cancelled (late payment), or with a mismatching amount: order goes
 *       to PAYMENT_REVIEW and is never auto-fulfilled.</li>
 *   <li>Failure/expiry: the attempt closes; once no attempt for the order is still open, the order is cancelled and
 *       its reserved stock released.</li>
 *   <li>Re-applying an outcome that is already reflected is a no-op.</li>
 * </ul>
 */
@Service
public class PaymentOutcomeService {

    private static final Logger log = LoggerFactory.getLogger(PaymentOutcomeService.class);
    private static final EnumSet<PaymentAttemptStatus> OPEN = EnumSet.of(PaymentAttemptStatus.CREATED,
            PaymentAttemptStatus.PENDING);

    private final PaymentAttemptRepository attempts;
    private final OrderPaymentService orderPayments;
    private final Clock clock;

    public PaymentOutcomeService(PaymentAttemptRepository attempts, OrderPaymentService orderPayments, Clock clock) {
        this.attempts = attempts;
        this.orderPayments = orderPayments;
        this.clock = clock;
    }

    public enum Outcome {
        SUCCEEDED,
        FAILED,
        EXPIRED
    }

    public enum Result {
        APPLIED,
        NEEDS_REVIEW,
        NO_CHANGE,
        UNKNOWN_ATTEMPT
    }

    @Transactional
    public Result apply(String attemptId, Outcome outcome, BigDecimal reportedAmount, String reportedCurrency) {
        Optional<Long> orderId = attemptId == null ? Optional.empty() : attempts.findOrderIdById(attemptId);
        if (orderId.isEmpty()) {
            log.warn("Payment outcome {} for unknown attempt {}", outcome, attemptId);
            return Result.UNKNOWN_ATTEMPT;
        }
        // Lock the order first, then read the attempt, so concurrent events for one order serialize.
        Order order = orderPayments.lock(orderId.get());
        PaymentAttempt attempt = attempts.findById(attemptId).orElseThrow();
        Instant now = clock.instant();

        return switch (outcome) {
            case SUCCEEDED -> applySuccess(order, attempt, reportedAmount, reportedCurrency, now);
            case FAILED, EXPIRED -> applyClosure(order, attempt, outcome, now);
        };
    }

    /** Cancels an unpaid order whose reservation expired when it has no open payment attempt left. */
    @Transactional
    public Result cancelIfNothingOpen(Long orderId) {
        Order order = orderPayments.lock(orderId);
        if (!order.isAwaitingPayment() || attempts.countByOrderIdAndStatusIn(orderId, OPEN) > 0) {
            return Result.NO_CHANGE;
        }
        orderPayments.cancelUnpaid(order);
        log.info("Order {} cancelled: payment window closed without a successful payment.", order.getOrderNumber());
        return Result.APPLIED;
    }

    private Result applySuccess(Order order, PaymentAttempt attempt, BigDecimal amount, String currency, Instant now) {
        if (attempt.getStatus() == PaymentAttemptStatus.SUCCEEDED) {
            return Result.NO_CHANGE;
        }
        attempt.markSucceeded(now);

        if (!attempt.matches(amount, currency)) {
            attempt.noteProblem("Provider reported " + amount + " " + currency + "; expected "
                    + attempt.getAmount() + " " + attempt.getCurrency(), now);
            if (!isFulfillmentStarted(order)) {
                orderPayments.flagForReview(order);
            }
            log.error("Amount/currency mismatch on attempt {} for order {}", attempt.getId(), order.getOrderNumber());
            return Result.NEEDS_REVIEW;
        }

        switch (order.getStatus()) {
            case PENDING_PAYMENT -> {
                orderPayments.confirmPaid(order);
                log.info("Order {} paid via attempt {}.", order.getOrderNumber(), attempt.getId());
                return Result.APPLIED;
            }
            case CANCELLED, PAYMENT_REVIEW -> {
                // Stock was already released: route to manual reconciliation (re-stock or refund) before fulfillment.
                attempt.noteProblem("Payment succeeded after the order was cancelled.", now);
                orderPayments.flagForReview(order);
                log.warn("Late payment on cancelled order {} (attempt {}); flagged for review.",
                        order.getOrderNumber(), attempt.getId());
                return Result.NEEDS_REVIEW;
            }
            default -> {
                // Order already paid by another attempt: a duplicate charge that needs a refund.
                attempt.noteProblem("Duplicate payment: order already " + order.getStatus() + "; refund required.", now);
                log.error("Duplicate payment on order {} (attempt {}).", order.getOrderNumber(), attempt.getId());
                return Result.NEEDS_REVIEW;
            }
        }
    }

    private Result applyClosure(Order order, PaymentAttempt attempt, Outcome outcome, Instant now) {
        if (!attempt.isOpen()) {
            return Result.NO_CHANGE;
        }
        if (outcome == Outcome.FAILED) {
            attempt.markFailed("Payment was declined or failed.", now);
        } else {
            attempt.markExpired(now);
        }
        boolean otherOpen = attempts.countByOrderIdAndStatusIn(order.getId(), OPEN) > 0;
        if (order.isAwaitingPayment() && !otherOpen) {
            orderPayments.cancelUnpaid(order);
            log.info("Order {} cancelled after payment {}; stock released.", order.getOrderNumber(),
                    outcome.name().toLowerCase());
        }
        return Result.APPLIED;
    }

    private static boolean isFulfillmentStarted(Order order) {
        return switch (order.getStatus()) {
            case PAID, SHIPPED, DELIVERED -> true;
            default -> false;
        };
    }
}
