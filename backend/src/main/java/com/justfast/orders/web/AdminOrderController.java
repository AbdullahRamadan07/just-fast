package com.justfast.orders.web;

import com.justfast.common.web.PageResponse;
import com.justfast.common.web.Paging;
import com.justfast.orders.application.OrderFulfillmentService;
import com.justfast.orders.application.OrderQueryService;
import com.justfast.orders.application.OrderViews.OrderDetail;
import com.justfast.orders.application.OrderViews.OrderSummary;
import com.justfast.orders.domain.OrderStatus;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderQueryService orderQueries;
    private final OrderFulfillmentService fulfillment;

    public AdminOrderController(OrderQueryService orderQueries, OrderFulfillmentService fulfillment) {
        this.orderQueries = orderQueries;
        this.fulfillment = fulfillment;
    }

    @GetMapping
    public PageResponse<OrderSummary> orders(@RequestParam(required = false) OrderStatus status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return orderQueries.allOrders(status,
                Paging.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending())));
    }

    @GetMapping("/stats")
    public Map<OrderStatus, Long> stats() {
        return orderQueries.countsByStatus();
    }

    @GetMapping("/{id}")
    public OrderDetail order(@PathVariable Long id) {
        return orderQueries.anyOrder(id);
    }

    @PostMapping("/{id}/ship")
    public OrderDetail ship(@PathVariable Long id) {
        fulfillment.markShipped(id);
        return orderQueries.anyOrder(id);
    }

    @PostMapping("/{id}/deliver")
    public OrderDetail deliver(@PathVariable Long id) {
        fulfillment.markDelivered(id);
        return orderQueries.anyOrder(id);
    }
}
