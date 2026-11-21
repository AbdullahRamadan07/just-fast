package com.justfast.catalog.web;

import com.justfast.catalog.domain.Category;
import com.justfast.catalog.domain.Product;
import com.justfast.catalog.domain.ProductStatus;
import com.justfast.inventory.application.InventoryService.StockLevel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CategoryResponse(Long id, String name, String slug) {

        public static CategoryResponse from(Category category) {
            return new CategoryResponse(category.getId(), category.getName(), category.getSlug());
        }
    }

    /** Storefront view: shows availability, never internal stock counts. */
    public record ProductSummary(Long id, String sku, String name, String description, BigDecimal price,
                                 String currency, String imageUrl, CategoryResponse category,
                                 int availableQuantity, boolean inStock) {

        public static ProductSummary from(Product product, String currency, StockLevel stock) {
            int available = Math.max(stock.available(), 0);
            return new ProductSummary(product.getId(), product.getSku(), product.getName(), product.getDescription(),
                    product.getPrice(), currency, product.getImageUrl(), CategoryResponse.from(product.getCategory()),
                    available, available > 0);
        }
    }

    /** Admin view: includes status and stock breakdown. */
    public record AdminProductResponse(Long id, String sku, String name, String description, BigDecimal price,
                                       String imageUrl, CategoryResponse category, ProductStatus status,
                                       int onHand, int reserved, int available) {

        public static AdminProductResponse from(Product product, StockLevel stock) {
            return new AdminProductResponse(product.getId(), product.getSku(), product.getName(),
                    product.getDescription(), product.getPrice(), product.getImageUrl(),
                    CategoryResponse.from(product.getCategory()), product.getStatus(),
                    stock.onHand(), stock.reserved(), stock.available());
        }
    }

    public record CreateProductRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Use letters, digits, '.', '_' or '-'.")
            String sku,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 5000) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @Size(max = 500) @Pattern(regexp = "^(https?://.*|/.*)?$", message = "Use an http(s) URL or a site path.")
            String imageUrl,
            @NotNull Long categoryId,
            @NotNull @Min(0) @Max(1_000_000) Integer initialStock) {
    }

    public record UpdateProductRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 5000) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @Size(max = 500) @Pattern(regexp = "^(https?://.*|/.*)?$", message = "Use an http(s) URL or a site path.")
            String imageUrl,
            @NotNull Long categoryId) {
    }

    public record CategoryRequest(@NotBlank @Size(max = 80) String name) {
    }
}
