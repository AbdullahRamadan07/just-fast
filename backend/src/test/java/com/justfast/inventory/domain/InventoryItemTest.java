package com.justfast.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.justfast.common.error.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InventoryItemTest {

    @Test
    void availableExcludesReservedUnits() {
        InventoryItem item = new InventoryItem(1L, 10);
        ReflectionTestUtils.setField(item, "reserved", 3);
        assertThat(item.available()).isEqualTo(7);
    }

    @Test
    void stockCannotDropBelowReserved() {
        InventoryItem item = new InventoryItem(1L, 10);
        ReflectionTestUtils.setField(item, "reserved", 4);
        assertThatThrownBy(() -> item.setOnHand(3)).isInstanceOf(BusinessRuleException.class);
        item.setOnHand(4);
        assertThat(item.available()).isZero();
    }
}
