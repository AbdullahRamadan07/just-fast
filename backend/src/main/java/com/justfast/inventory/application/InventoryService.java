package com.justfast.inventory.application;

import com.justfast.common.error.NotFoundException;
import com.justfast.inventory.domain.InventoryItem;
import com.justfast.inventory.infrastructure.InventoryItemRepository;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stock queries for the storefront and stock counts for admins. */
@Service
public class InventoryService {

    private final InventoryItemRepository inventory;

    public InventoryService(InventoryItemRepository inventory) {
        this.inventory = inventory;
    }

    public record StockLevel(int onHand, int reserved, int available) {

        static StockLevel of(InventoryItem item) {
            return new StockLevel(item.getOnHand(), item.getReserved(), item.available());
        }

        static final StockLevel NONE = new StockLevel(0, 0, 0);
    }

    @Transactional(readOnly = true)
    public Map<Long, StockLevel> stockLevels(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return inventory.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(InventoryItem::getProductId, StockLevel::of, (a, b) -> a));
    }

    @Transactional(readOnly = true)
    public StockLevel stockLevel(Long productId) {
        return inventory.findById(productId).map(StockLevel::of).orElse(StockLevel.NONE);
    }

    public static StockLevel levelOrNone(Map<Long, StockLevel> levels, Long productId) {
        return levels.getOrDefault(productId, StockLevel.NONE);
    }

    @Transactional
    public void createStock(Long productId, int initialOnHand) {
        inventory.save(new InventoryItem(productId, initialOnHand));
    }

    @Transactional
    public StockLevel setOnHand(Long productId, int onHand) {
        InventoryItem item = inventory.findById(productId)
                .orElseThrow(() -> new NotFoundException("Inventory record not found."));
        item.setOnHand(onHand);
        return StockLevel.of(item);
    }
}
