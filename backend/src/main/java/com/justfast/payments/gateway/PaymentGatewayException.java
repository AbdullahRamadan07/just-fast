package com.justfast.payments.gateway;

/** The provider could not be reached or rejected the call. The outcome of the call may be unknown. */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
