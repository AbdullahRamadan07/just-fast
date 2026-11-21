package com.justfast.orders.application;

import com.justfast.common.error.NotFoundException;
import com.justfast.orders.domain.Order;
import com.justfast.orders.infrastructure.OrderRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin fulfillment. The Order aggregate only allows shipping PAID orders, so unpaid orders can never ship. */
@Service
@Transactional
public class OrderFulfillmentService {

    private final OrderRepository orders;
    private final Clock clock;

    public OrderFulfillmentService(OrderRepository orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    public void markShipped(Long orderId) {
        require(orderId).ship(clock.instant());
    }

    public void markDelivered(Long orderId) {
        require(orderId).deliver(clock.instant());
    }

    private Order require(Long orderId) {
        return orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order not found."));
    }
}
