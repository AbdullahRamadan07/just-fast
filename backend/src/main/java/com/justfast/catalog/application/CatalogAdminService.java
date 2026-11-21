package com.justfast.catalog.application;

import com.justfast.catalog.domain.Category;
import com.justfast.catalog.domain.Product;
import com.justfast.catalog.domain.Product.ProductDetails;
import com.justfast.catalog.infrastructure.CategoryRepository;
import com.justfast.catalog.infrastructure.ProductRepository;
import com.justfast.common.error.BusinessRuleException;
import com.justfast.common.error.NotFoundException;
import com.justfast.inventory.application.InventoryService;
import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalog management for administrators. Products are archived, never deleted, to keep order history intact. */
@Service
@Transactional
public class CatalogAdminService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final InventoryService inventoryService;
    private final Clock clock;

    public CatalogAdminService(ProductRepository products, CategoryRepository categories,
                               InventoryService inventoryService, Clock clock) {
        this.products = products;
        this.categories = categories;
        this.inventoryService = inventoryService;
        this.clock = clock;
    }

    public Product createProduct(String sku, ProductDetails details, Long categoryId, int initialStock) {
        String normalizedSku = Product.normalizeSku(sku);
        if (products.existsBySku(normalizedSku)) {
            throw skuTaken(normalizedSku);
        }
        try {
            Product product = products.saveAndFlush(
                    new Product(normalizedSku, details, requireCategory(categoryId), clock.instant()));
            inventoryService.createStock(product.getId(), initialStock);
            return product;
        } catch (DataIntegrityViolationException race) {
            throw skuTaken(normalizedSku);
        }
    }

    public Product updateProduct(Long productId, ProductDetails details, Long categoryId) {
        Product product = requireProduct(productId);
        product.update(details, requireCategory(categoryId), clock.instant());
        return product;
    }

    public Product archiveProduct(Long productId) {
        Product product = requireProduct(productId);
        product.archive(clock.instant());
        return product;
    }

    public Product restoreProduct(Long productId) {
        Product product = requireProduct(productId);
        product.restore(clock.instant());
        return product;
    }

    public Category createCategory(String name) {
        Category category = new Category(name);
        if (categories.existsBySlug(category.getSlug())) {
            throw categoryTaken();
        }
        return categories.save(category);
    }

    public Category renameCategory(Long categoryId, String name) {
        Category category = requireCategory(categoryId);
        Category probe = new Category(name);
        categories.findBySlug(probe.getSlug())
                .filter(other -> !other.getId().equals(categoryId))
                .ifPresent(other -> {
                    throw categoryTaken();
                });
        category.rename(name);
        return category;
    }

    public void deleteCategory(Long categoryId) {
        Category category = requireCategory(categoryId);
        if (products.existsByCategoryId(categoryId)) {
            throw new BusinessRuleException("CATEGORY_IN_USE", "Move or archive this category's products first.");
        }
        categories.delete(category);
    }

    private Product requireProduct(Long id) {
        return products.findWithCategoryById(id).orElseThrow(() -> new NotFoundException("Product not found."));
    }

    private Category requireCategory(Long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Category not found."));
    }

    private static BusinessRuleException skuTaken(String sku) {
        return new BusinessRuleException("SKU_TAKEN", "A product with SKU " + sku + " already exists.");
    }

    private static BusinessRuleException categoryTaken() {
        return new BusinessRuleException("CATEGORY_EXISTS", "A category with this name already exists.");
    }
}
