package com.justfast.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url")
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private ProductStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Product(String sku, ProductDetails details, Category category, Instant now) {
        this.sku = normalizeSku(sku);
        this.status = ProductStatus.ACTIVE;
        this.createdAt = now;
        update(details, category, now);
    }

    public static String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    public void update(ProductDetails details, Category newCategory, Instant now) {
        if (details.price().signum() < 0) {
            throw new IllegalArgumentException("Price cannot be negative.");
        }
        this.name = details.name().trim();
        this.description = details.description();
        this.price = details.price().setScale(2, RoundingMode.HALF_UP);
        this.imageUrl = details.imageUrl() == null || details.imageUrl().isBlank() ? null : details.imageUrl().trim();
        this.category = newCategory;
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        this.status = ProductStatus.ARCHIVED;
        this.updatedAt = now;
    }

    public void restore(Instant now) {
        this.status = ProductStatus.ACTIVE;
        this.updatedAt = now;
    }

    public boolean isPurchasable() {
        return status == ProductStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Category getCategory() {
        return category;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public record ProductDetails(String name, String description, BigDecimal price, String imageUrl) {
    }
}
