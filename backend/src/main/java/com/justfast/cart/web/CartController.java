package com.justfast.cart.web;

import com.justfast.cart.application.CartService;
import com.justfast.cart.application.CartService.CartView;
import com.justfast.cart.domain.Cart;
import com.justfast.identity.security.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartView cart(@AuthenticationPrincipal AppUserPrincipal user) {
        return cartService.view(user.id());
    }

    /** Sets the absolute quantity of a product in the cart (0 removes it). */
    @PutMapping("/items/{productId}")
    public CartView setQuantity(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long productId,
                                @Valid @RequestBody SetQuantityRequest request) {
        return cartService.setQuantity(user.id(), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartView remove(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long productId) {
        return cartService.setQuantity(user.id(), productId, 0);
    }

    @DeleteMapping
    public CartView clear(@AuthenticationPrincipal AppUserPrincipal user) {
        return cartService.clear(user.id());
    }

    public record SetQuantityRequest(@NotNull @Min(0) @Max(Cart.MAX_QUANTITY_PER_ITEM) Integer quantity) {
    }
}
