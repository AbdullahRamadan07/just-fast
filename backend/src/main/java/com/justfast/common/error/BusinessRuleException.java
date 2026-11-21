package com.justfast.common.error;

/**
 * A request that is well-formed but violates a business rule (for example, insufficient stock
 * or an invalid order state transition). Mapped to HTTP 409 with a stable error code.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
