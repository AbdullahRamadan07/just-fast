package com.justfast.orders.application;

import com.justfast.common.error.NotFoundException;
import com.justfast.common.web.PageResponse;
import com.justfast.orders.application.OrderViews.OrderDetail;
import com.justfast.orders.application.OrderViews.OrderSummary;
import com.justfast.orders.application.PaymentStatusLookup.PaymentSummary;
import com.justfast.orders.domain.Order;
import com.justfast.orders.domain.OrderStatus;
import com.justfast.orders.infrastructure.OrderRepository;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orders;
    private final PaymentStatusLookup payments;
    private final Clock clock;

    public OrderQueryService(OrderRepository orders, PaymentStatusLookup payments, Clock clock) {
        this.orders = orders;
        this.payments = payments;
        this.clock = clock;
    }

    /** Customer's own orders only. */
    public PageResponse<OrderSummary> customerOrders(Long userId, Pageable pageable) {
        return summaries(orders.findByUserId(userId, pageable));
    }

    /** A customer can only see their own order; anything else is reported as not found. */
    public OrderDetail customerOrder(Long userId, Long orderId) {
        Order order = orders.findWithItemsByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new NotFoundException("Order not found."));
        return detail(order);
    }

    public PageResponse<OrderSummary> allOrders(OrderStatus status, Pageable pageable) {
        Page<Order> page = status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable);
        return summaries(page);
    }

    public OrderDetail anyOrder(Long orderId) {
        return detail(orders.findWithItemsById(orderId).orElseThrow(() -> new NotFoundException("Order not found.")));
    }

    public Map<OrderStatus, Long> countsByStatus() {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status, orders.countByStatus(status));
        }
        return counts;
    }

    private OrderDetail detail(Order order) {
        PaymentSummary payment = payments.latestFor(List.of(order.getId())).get(order.getId());
        return OrderDetail.of(order, payment, clock.instant());
    }

    private PageResponse<OrderSummary> summaries(Page<Order> page) {
        Map<Long, PaymentSummary> latest = payments.latestFor(page.map(Order::getId).getContent());
        return PageResponse.of(page, order -> OrderSummary.of(order, latest.get(order.getId())));
    }
}
