package com.justfast.catalog.application;

import com.justfast.catalog.domain.Category;
import com.justfast.catalog.domain.Product;
import com.justfast.catalog.domain.ProductStatus;
import com.justfast.catalog.infrastructure.CategoryRepository;
import com.justfast.catalog.infrastructure.ProductRepository;
import com.justfast.common.error.NotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-side catalog use cases for the storefront and other modules. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final ProductRepository products;
    private final CategoryRepository categories;

    public CatalogService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    public record ProductSearch(String query, String categorySlug, boolean includeArchived) {
    }

    public Page<Product> search(ProductSearch search, Pageable pageable) {
        Long categoryId = null;
        if (search.categorySlug() != null && !search.categorySlug().isBlank()) {
            var category = categories.findBySlug(search.categorySlug());
            if (category.isEmpty()) {
                return Page.empty(pageable);
            }
            categoryId = category.get().getId();
        }
        String term = search.query() == null || search.query().isBlank()
                ? null
                : search.query().trim().toLowerCase(Locale.ROOT);
        ProductStatus status = search.includeArchived() ? null : ProductStatus.ACTIVE;
        return products.search(status, categoryId, term, pageable);
    }

    public Product requireActiveProduct(Long id) {
        Product product = requireProduct(id);
        if (!product.isPurchasable()) {
            throw new NotFoundException("Product not found.");
        }
        return product;
    }

    public Product requireProduct(Long id) {
        return products.findWithCategoryById(id).orElseThrow(() -> new NotFoundException("Product not found."));
    }

    public List<Product> findAllById(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : products.findByIdIn(ids);
    }

    public List<Category> categories() {
        return categories.findAllByOrderByNameAsc();
    }
}
