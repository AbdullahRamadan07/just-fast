package com.justfast.cart.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CartTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    @Test
    void setsReplacesAndRemovesQuantities() {
        Cart cart = new Cart(1L, NOW);
        cart.setQuantity(10L, 2, NOW);
        cart.setQuantity(10L, 5, NOW);
        cart.setQuantity(11L, 1, NOW);
        assertThat(cart.getItems()).hasSize(2);
        assertThat(cart.find(10L)).get().extracting(CartItem::getQuantity).isEqualTo(5);

        cart.setQuantity(10L, 0, NOW);
        assertThat(cart.find(10L)).isEmpty();
    }

    @Test
    void rejectsOutOfRangeQuantities() {
        Cart cart = new Cart(1L, NOW);
        assertThatThrownBy(() -> cart.setQuantity(10L, -1, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cart.setQuantity(10L, Cart.MAX_QUANTITY_PER_ITEM + 1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
