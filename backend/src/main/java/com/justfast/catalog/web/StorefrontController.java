package com.justfast.catalog.web;

import com.justfast.catalog.application.CatalogService;
import com.justfast.catalog.application.CatalogService.ProductSearch;
import com.justfast.catalog.domain.Product;
import com.justfast.catalog.web.CatalogDtos.CategoryResponse;
import com.justfast.catalog.web.CatalogDtos.ProductSummary;
import com.justfast.common.config.StoreProperties;
import com.justfast.common.web.PageResponse;
import com.justfast.common.web.Paging;
import com.justfast.inventory.application.InventoryService;
import com.justfast.inventory.application.InventoryService.StockLevel;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StorefrontController {

    private final CatalogService catalogService;
    private final InventoryService inventoryService;
    private final StoreProperties store;

    public StorefrontController(CatalogService catalogService, InventoryService inventoryService, StoreProperties store) {
        this.catalogService = catalogService;
        this.inventoryService = inventoryService;
        this.store = store;
    }

    @GetMapping("/products")
    public PageResponse<ProductSummary> products(@RequestParam(required = false) String q,
                                                 @RequestParam(required = false) String category,
                                                 @RequestParam(defaultValue = "name") String sort,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "12") int size) {
        Page<Product> result = catalogService.search(new ProductSearch(q, category, false),
                Paging.of(page, size, sortOf(sort)));
        Map<Long, StockLevel> stock = inventoryService.stockLevels(result.map(Product::getId).getContent());
        List<ProductSummary> items = result.getContent().stream()
                .map(p -> ProductSummary.from(p, store.currency(), InventoryService.levelOrNone(stock, p.getId())))
                .toList();
        return PageResponse.of(result, items);
    }

    @GetMapping("/products/{id}")
    public ProductSummary product(@PathVariable Long id) {
        Product product = catalogService.requireActiveProduct(id);
        return ProductSummary.from(product, store.currency(), inventoryService.stockLevel(id));
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalogService.categories().stream().map(CategoryResponse::from).toList();
    }

    /** Public store configuration used by the storefront (currency, shipping region, flat fee). */
    @GetMapping("/store")
    public StoreInfo storeInfo() {
        return new StoreInfo(store.currency(), store.shippingCountry(), store.flatShippingFee());
    }

    public record StoreInfo(String currency, String shippingCountry, BigDecimal flatShippingFee) {
    }

    private static Sort sortOf(String sort) {
        return switch (sort) {
            case "price" -> Sort.by("price").ascending().and(Sort.by("id"));
            case "-price" -> Sort.by("price").descending().and(Sort.by("id"));
            case "newest" -> Sort.by("createdAt").descending().and(Sort.by("id"));
            default -> Sort.by("name").ascending().and(Sort.by("id"));
        };
    }
}
