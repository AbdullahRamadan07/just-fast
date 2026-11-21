package com.justfast.cart.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** A customer's cart. One per user; items reference products by id. */
@Entity
@Table(name = "carts")
public class Cart {

    public static final int MAX_QUANTITY_PER_ITEM = 99;
    public static final int MAX_DISTINCT_ITEMS = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {
    }

    public Cart(Long userId, Instant now) {
        this.userId = userId;
        this.updatedAt = now;
    }

    /** Sets the quantity for a product; zero removes it. */
    public void setQuantity(Long productId, int quantity, Instant now) {
        if (quantity < 0 || quantity > MAX_QUANTITY_PER_ITEM) {
            throw new IllegalArgumentException("Quantity must be between 0 and " + MAX_QUANTITY_PER_ITEM + ".");
        }
        Optional<CartItem> existing = find(productId);
        if (quantity == 0) {
            existing.ifPresent(items::remove);
        } else if (existing.isPresent()) {
            existing.get().changeQuantity(quantity);
        } else {
            if (items.size() >= MAX_DISTINCT_ITEMS) {
                throw new IllegalArgumentException("The cart is full.");
            }
            items.add(new CartItem(this, productId, quantity));
        }
        this.updatedAt = now;
    }

    public void clear(Instant now) {
        items.clear();
        this.updatedAt = now;
    }

    public Optional<CartItem> find(Long productId) {
        return items.stream().filter(i -> i.getProductId().equals(productId)).findFirst();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
