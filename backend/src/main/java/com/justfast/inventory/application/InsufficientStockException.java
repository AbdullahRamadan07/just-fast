package com.justfast.inventory.application;

import com.justfast.common.error.BusinessRuleException;

public class InsufficientStockException extends BusinessRuleException {

    private final Long productId;

    public InsufficientStockException(Long productId, String productName) {
        super("INSUFFICIENT_STOCK", "Not enough stock for \"" + productName + "\".");
        this.productId = productId;
    }

    public Long productId() {
        return productId;
    }
}
