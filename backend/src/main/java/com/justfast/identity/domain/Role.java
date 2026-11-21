package com.justfast.identity.domain;

public enum Role {
    CUSTOMER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
