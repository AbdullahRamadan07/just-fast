package com.justfast.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A shipping address owned by one customer. */
@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String line1;

    private String line2;

    @Column(nullable = false)
    private String city;

    private String region;

    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @Column(nullable = false, length = 2)
    private String country;

    private String phone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Address() {
    }

    public Address(Long userId, AddressDetails details, Instant createdAt) {
        this.userId = userId;
        this.createdAt = createdAt;
        apply(details);
    }

    public void apply(AddressDetails details) {
        this.fullName = details.fullName().trim();
        this.line1 = details.line1().trim();
        this.line2 = blankToNull(details.line2());
        this.city = details.city().trim();
        this.region = blankToNull(details.region());
        this.postalCode = details.postalCode().trim();
        this.country = details.country().trim().toUpperCase();
        this.phone = blankToNull(details.phone());
    }

    public boolean isOwnedBy(Long candidateUserId) {
        return userId.equals(candidateUserId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public AddressDetails details() {
        return new AddressDetails(fullName, line1, line2, city, region, postalCode, country, phone);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Value object describing an address, shared with order snapshots. */
    public record AddressDetails(String fullName, String line1, String line2, String city, String region,
                                 String postalCode, String country, String phone) {
    }
}
