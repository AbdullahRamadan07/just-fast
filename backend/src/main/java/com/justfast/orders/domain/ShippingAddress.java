package com.justfast.orders.domain;

import com.justfast.identity.domain.Address.AddressDetails;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Purchase-time copy of the shipping address; later edits to the customer's address book do not change it. */
@Embeddable
public class ShippingAddress {

    @Column(name = "ship_full_name", nullable = false)
    private String fullName;

    @Column(name = "ship_line1", nullable = false)
    private String line1;

    @Column(name = "ship_line2")
    private String line2;

    @Column(name = "ship_city", nullable = false)
    private String city;

    @Column(name = "ship_region")
    private String region;

    @Column(name = "ship_postal_code", nullable = false)
    private String postalCode;

    @Column(name = "ship_country", nullable = false, length = 2)
    private String country;

    @Column(name = "ship_phone")
    private String phone;

    protected ShippingAddress() {
    }

    public static ShippingAddress snapshotOf(AddressDetails d) {
        ShippingAddress a = new ShippingAddress();
        a.fullName = d.fullName();
        a.line1 = d.line1();
        a.line2 = d.line2();
        a.city = d.city();
        a.region = d.region();
        a.postalCode = d.postalCode();
        a.country = d.country();
        a.phone = d.phone();
        return a;
    }

    public AddressDetails toDetails() {
        return new AddressDetails(fullName, line1, line2, city, region, postalCode, country, phone);
    }
}
