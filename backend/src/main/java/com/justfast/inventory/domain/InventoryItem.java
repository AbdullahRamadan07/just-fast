package com.justfast.inventory.domain;

import com.justfast.common.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Stock level for one product. {@code reserved} units are held by unpaid orders;
 * {@code available = onHand - reserved} is what customers can still buy.
 */
@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(nullable = false)
    private int reserved;

    @Version
    private long version;

    protected InventoryItem() {
    }

    public InventoryItem(Long productId, int onHand) {
        if (onHand < 0) {
            throw new IllegalArgumentException("Stock cannot be negative.");
        }
        this.productId = productId;
        this.onHand = onHand;
        this.reserved = 0;
    }

    /** Admin stock count. Cannot drop below what unpaid orders currently hold. */
    public void setOnHand(int newOnHand) {
        if (newOnHand < 0) {
            throw new BusinessRuleException("INVALID_STOCK", "Stock cannot be negative.");
        }
        if (newOnHand < reserved) {
            throw new BusinessRuleException("STOCK_BELOW_RESERVED",
                    "Stock cannot be lower than the " + reserved + " units reserved by pending orders.");
        }
        this.onHand = newOnHand;
    }

    public int available() {
        return onHand - reserved;
    }

    public Long getProductId() {
        return productId;
    }

    public int getOnHand() {
        return onHand;
    }

    public int getReserved() {
        return reserved;
    }
}
