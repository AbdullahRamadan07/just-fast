package com.justfast.catalog.infrastructure;

import com.justfast.catalog.domain.Product;
import com.justfast.catalog.domain.ProductStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Catalog search. {@code status}, {@code categoryId} and {@code term} are optional filters;
     * {@code term} is matched against name and SKU.
     */
    @EntityGraph(attributePaths = "category")
    @Query("""
            select p from Product p
            where (:status is null or p.status = :status)
              and (:categoryId is null or p.category.id = :categoryId)
              and (:term is null
                   or lower(p.name) like concat('%', :term, '%')
                   or lower(p.sku) like concat('%', :term, '%'))
            """)
    Page<Product> search(@Param("status") ProductStatus status,
                         @Param("categoryId") Long categoryId,
                         @Param("term") String term,
                         Pageable pageable);

    @EntityGraph(attributePaths = "category")
    List<Product> findByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findWithCategoryById(Long id);

    boolean existsBySku(String sku);

    boolean existsByCategoryId(Long categoryId);
}
