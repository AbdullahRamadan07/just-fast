package com.justfast.catalog.web;

import com.justfast.catalog.application.CatalogAdminService;
import com.justfast.catalog.application.CatalogService;
import com.justfast.catalog.application.CatalogService.ProductSearch;
import com.justfast.catalog.domain.Product;
import com.justfast.catalog.domain.Product.ProductDetails;
import com.justfast.catalog.web.CatalogDtos.AdminProductResponse;
import com.justfast.catalog.web.CatalogDtos.CategoryRequest;
import com.justfast.catalog.web.CatalogDtos.CategoryResponse;
import com.justfast.catalog.web.CatalogDtos.CreateProductRequest;
import com.justfast.catalog.web.CatalogDtos.UpdateProductRequest;
import com.justfast.common.web.PageResponse;
import com.justfast.common.web.Paging;
import com.justfast.inventory.application.InventoryService;
import com.justfast.inventory.application.InventoryService.StockLevel;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminCatalogController {

    private final CatalogService catalogService;
    private final CatalogAdminService adminService;
    private final InventoryService inventoryService;

    public AdminCatalogController(CatalogService catalogService, CatalogAdminService adminService,
                                  InventoryService inventoryService) {
        this.catalogService = catalogService;
        this.adminService = adminService;
        this.inventoryService = inventoryService;
    }

    @GetMapping("/products")
    public PageResponse<AdminProductResponse> products(@RequestParam(required = false) String q,
                                                       @RequestParam(required = false) String category,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        Page<Product> result = catalogService.search(new ProductSearch(q, category, true),
                Paging.of(page, size, Sort.by("name").and(Sort.by("id"))));
        Map<Long, StockLevel> stock = inventoryService.stockLevels(result.map(Product::getId).getContent());
        List<AdminProductResponse> items = result.getContent().stream()
                .map(p -> AdminProductResponse.from(p, InventoryService.levelOrNone(stock, p.getId())))
                .toList();
        return PageResponse.of(result, items);
    }

    @GetMapping("/products/{id}")
    public AdminProductResponse product(@PathVariable Long id) {
        return toResponse(catalogService.requireProduct(id));
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        Product product = adminService.createProduct(request.sku(),
                new ProductDetails(request.name(), request.description(), request.price(), request.imageUrl()),
                request.categoryId(), request.initialStock());
        return toResponse(product);
    }

    @PutMapping("/products/{id}")
    public AdminProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        Product product = adminService.updateProduct(id,
                new ProductDetails(request.name(), request.description(), request.price(), request.imageUrl()),
                request.categoryId());
        return toResponse(product);
    }

    @PostMapping("/products/{id}/archive")
    public AdminProductResponse archive(@PathVariable Long id) {
        return toResponse(adminService.archiveProduct(id));
    }

    @PostMapping("/products/{id}/restore")
    public AdminProductResponse restore(@PathVariable Long id) {
        return toResponse(adminService.restoreProduct(id));
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalogService.categories().stream().map(CategoryResponse::from).toList();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return CategoryResponse.from(adminService.createCategory(request.name()));
    }

    @PutMapping("/categories/{id}")
    public CategoryResponse renameCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return CategoryResponse.from(adminService.renameCategory(id, request.name()));
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        adminService.deleteCategory(id);
    }

    private AdminProductResponse toResponse(Product product) {
        return AdminProductResponse.from(product, inventoryService.stockLevel(product.getId()));
    }
}
