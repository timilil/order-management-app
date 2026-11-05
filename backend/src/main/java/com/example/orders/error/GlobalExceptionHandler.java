package com.example.orders.error;

import com.example.orders.domain.InvalidStatusTransitionException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception that reaches the controllers into the one {@link ErrorResponse} shape. Messages are
 * written here and never taken from an exception, unless the exception is one of ours and was written for
 * clients. Stack traces, SQL and class names are logged on the server only.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring's own web exceptions (unsupported method,
 * media type, unknown path, and so on) get the same shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    // ---- Our own exceptions ----

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<Object> orderNotFound(HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found.", request);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    ResponseEntity<Object> invalidTransition(InvalidStatusTransitionException ex, HttpServletRequest request) {
        // The message only names two statuses, so it is safe to show.
        return respond(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", ex.getMessage(), request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> concurrentModification(HttpServletRequest request) {
        return respond(
                HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION",
                "The order was changed by someone else. Reload it and try again.",
                request);
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<Object> invalidRequest(InvalidRequestException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), request);
    }

    // ---- Security exceptions thrown inside controllers or services ----
    // These must be handled here, or the catch-all below would turn them into a 500. They use the same
    // codes and messages as the filter-level handlers in the security package.

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> accessDenied(HttpServletRequest request) {
        return respond(
                HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this operation.", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> authenticationFailed(AuthenticationException ex, HttpServletRequest request) {
        String message = ex instanceof BadCredentialsException
                ? "Invalid username or password."
                : "Authentication is required. Provide a valid bearer token.";
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message, headers, request, null);
    }

    // ---- Last resort ----

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.",
                request);
    }

    // ---- Spring's own web exceptions ----

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorResponse.FieldError(e.getField(), e.getDefaultMessage()))
                .sorted(Comparator.comparing(ErrorResponse.FieldError::field)
                        .thenComparing(ErrorResponse.FieldError::message, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        return validationFailed(fields, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorResponse.FieldError> fields = ex.getAllErrors().stream()
                .map(e -> new ErrorResponse.FieldError("request", e.getDefaultMessage()))
                .toList();
        return validationFailed(fields, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // The Jackson message can contain class names and internal paths, so it is not shown.
        return build(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST",
                "The request body is missing or is not valid JSON for this operation.",
                headers,
                request,
                null);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        String message = name == null ? "A parameter has an invalid value." : "Parameter '" + name + "' has an invalid value.";
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, headers, request, null);
    }

    /** Everything else from Spring MVC (405, 415, 404 for unknown paths, missing parameters, ...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        if (status.is5xxServerError()) {
            log.error("Server error from the web layer", ex);
            return build(status, "INTERNAL_ERROR", "An unexpected error occurred. Please try again later.", headers, request, null);
        }
        String code = status == HttpStatus.BAD_REQUEST ? "INVALID_REQUEST" : status.name();
        String message = status == HttpStatus.BAD_REQUEST ? "The request is invalid." : status.getReasonPhrase() + ".";
        return build(status, code, message, headers, request, null);
    }

    // ---- helpers ----

    private ResponseEntity<Object> validationFailed(
            List<ErrorResponse.FieldError> fields, HttpHeaders headers, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request data is not valid.", headers, request, fields);
    }

    private ResponseEntity<Object> respond(HttpStatus status, String code, String message, HttpServletRequest request) {
        return build(status, code, message, new HttpHeaders(), request.getRequestURI(), null);
    }

    private ResponseEntity<Object> build(
            HttpStatus status,
            String code,
            String message,
            HttpHeaders headers,
            WebRequest request,
            List<ErrorResponse.FieldError> fields) {
        String path = request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : null;
        return build(status, code, message, headers, path, fields);
    }

    private ResponseEntity<Object> build(
            HttpStatus status,
            String code,
            String message,
            HttpHeaders headers,
            HttpServletRequest request,
            List<ErrorResponse.FieldError> fields) {
        return build(status, code, message, headers, request.getRequestURI(), fields);
    }

    private ResponseEntity<Object> build(
            HttpStatus status,
            String code,
            String message,
            HttpHeaders headers,
            String path,
            List<ErrorResponse.FieldError> fields) {
        ErrorResponse body = new ErrorResponse(status.value(), code, message, Instant.now(clock), path, fields);
        HttpHeaders out = new HttpHeaders();
        out.putAll(headers);
        out.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(body, out, status);
    }
}
