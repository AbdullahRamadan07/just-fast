package com.justfast.orders.web;

import com.justfast.common.web.PageResponse;
import com.justfast.common.web.Paging;
import com.justfast.identity.security.AppUserPrincipal;
import com.justfast.orders.application.OrderQueryService;
import com.justfast.orders.application.OrderViews.OrderDetail;
import com.justfast.orders.application.OrderViews.OrderSummary;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderQueryService orderQueries;

    public OrderController(OrderQueryService orderQueries) {
        this.orderQueries = orderQueries;
    }

    @GetMapping
    public PageResponse<OrderSummary> myOrders(@AuthenticationPrincipal AppUserPrincipal user,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return orderQueries.customerOrders(user.id(),
                Paging.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending())));
    }

    @GetMapping("/{id}")
    public OrderDetail myOrder(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long id) {
        return orderQueries.customerOrder(user.id(), id);
    }
}
