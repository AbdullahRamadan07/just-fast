package com.justfast.checkout;

import com.justfast.checkout.CheckoutService.CheckoutResult;
import com.justfast.identity.security.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    /**
     * Places the order for the current cart and returns the provider's hosted checkout URL.
     * The client generates {@code idempotencyKey} once per checkout attempt and reuses it on retries.
     */
    @PostMapping("/api/checkout")
    public CheckoutResult checkout(@AuthenticationPrincipal AppUserPrincipal user,
                                   @Valid @RequestBody CheckoutRequest request) {
        return checkoutService.checkout(user.id(), user.email(), request.addressId(), request.idempotencyKey());
    }

    @PostMapping("/api/orders/{orderId}/payment-session")
    public CheckoutResult resumePayment(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long orderId) {
        return checkoutService.resumePayment(user.id(), user.email(), orderId);
    }

    public record CheckoutRequest(
            @NotNull Long addressId,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{8,64}", message = "Invalid idempotency key.") String idempotencyKey) {
    }
}
