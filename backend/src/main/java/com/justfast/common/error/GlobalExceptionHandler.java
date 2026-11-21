package com.justfast.common.error;

import com.justfast.payments.gateway.PaymentGatewayException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return respond(HttpStatus.BAD_REQUEST,
                ApiError.withFields(400, "VALIDATION_FAILED", "Some fields are invalid.", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    ResponseEntity<ApiError> badRequest(Exception ex) {
        return respond(HttpStatus.BAD_REQUEST, ApiError.of(400, "BAD_REQUEST", "The request could not be read."));
    }

    @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ApiError> notFound(Exception ex) {
        String message = ex instanceof NotFoundException ? ex.getMessage() : "Not found.";
        return respond(HttpStatus.NOT_FOUND, ApiError.of(404, "NOT_FOUND", message));
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ApiError> businessRule(BusinessRuleException ex) {
        return respond(HttpStatus.CONFLICT, ApiError.of(409, ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> concurrentUpdate(OptimisticLockingFailureException ex) {
        return respond(HttpStatus.CONFLICT,
                ApiError.of(409, "CONCURRENT_UPDATE", "The resource was changed by another request. Please retry."));
    }

    @ExceptionHandler(PaymentGatewayException.class)
    ResponseEntity<ApiError> gateway(PaymentGatewayException ex) {
        log.warn("Payment provider call failed: {}", ex.getMessage());
        return respond(HttpStatus.BAD_GATEWAY, ApiError.of(502, "PAYMENT_PROVIDER_UNAVAILABLE",
                "The payment provider could not be reached. Please try again."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, ApiError.of(403, "FORBIDDEN", "You do not have access to this resource."));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> unauthorized(AuthenticationException ex) {
        return respond(HttpStatus.UNAUTHORIZED, ApiError.of(401, "UNAUTHORIZED", "Invalid email or password."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        // Spring MVC's own exceptions (405, 415, missing parameter, ...) carry their proper status.
        if (ex instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            return ResponseEntity.status(status).body(ApiError.of(status, "REQUEST_REJECTED",
                    errorResponse.getBody().getDetail() != null ? errorResponse.getBody().getDetail() : "Request rejected."));
        }
        log.error("Unhandled error", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR,
                ApiError.of(500, "INTERNAL_ERROR", "Something went wrong. Please try again."));
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, ApiError body) {
        return ResponseEntity.status(status).body(body);
    }
}
