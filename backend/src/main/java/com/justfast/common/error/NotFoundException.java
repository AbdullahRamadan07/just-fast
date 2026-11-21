package com.justfast.common.error;

/** The requested resource does not exist or is not visible to the caller. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
