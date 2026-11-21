package com.justfast.inventory.web;

import com.justfast.inventory.application.InventoryService;
import com.justfast.inventory.application.InventoryService.StockLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/inventory")
public class AdminInventoryController {

    private final InventoryService inventoryService;

    public AdminInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PutMapping("/{productId}")
    public StockLevel setStock(@PathVariable Long productId, @Valid @RequestBody SetStockRequest request) {
        return inventoryService.setOnHand(productId, request.onHand());
    }

    public record SetStockRequest(@NotNull @Min(0) @Max(1_000_000) Integer onHand) {
    }
}
