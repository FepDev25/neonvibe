package com.neonvibe.exception;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Global exception handler.
 *
 * <p>Converts any exception thrown inside a controller into the standard
 * error JSON contract: {@code {error, message, timestamp}}.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "validation_error");
        body.put("message", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Request validation failed"));
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({org.springframework.web.HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed", "HTTP method not supported");
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidToken(InvalidTokenException ex) {
        return build(HttpStatus.UNAUTHORIZED, "unauthorized", ex.getMessage());
    }

    @ExceptionHandler(AccountNotAllowedException.class)
    public ResponseEntity<Map<String, Object>> handleAccountNotAllowed(AccountNotAllowedException ex) {
        return build(HttpStatus.FORBIDDEN, "forbidden", ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "not_found", ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "bad_request", ex.getMessage());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public Object handleNoHandler(NoHandlerFoundException ex, HttpServletRequest request,
                                  HttpServletResponse response) throws ServletException, IOException {
        return spaForward(request, response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResource(NoResourceFoundException ex, HttpServletRequest request,
                                   HttpServletResponse response) throws ServletException, IOException {
        return spaForward(request, response);
    }

    /**
     * SPA fallback: unknown non-API GETs that don't look like files are forwarded
     * to index.html so client-side routing works on deep links. Real static assets
     * ({@code /assets/x.js}, {@code /sw.js}, {@code /manifest.webmanifest}) keep
     * their 404. API, WebSocket and actuator paths are never forwarded.
     *
     * <p>Uses an explicit {@link RequestDispatcher} forward because this advice is a
     * {@code @RestControllerAdvice}: returning a view-name String would be written
     * to the body verbatim instead of dispatched.</p>
     */
    private Object spaForward(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (isSpaEligible(request)) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return null;
        }
        return build(HttpStatus.NOT_FOUND, "not_found", "Resource not found");
    }

    private boolean isSpaEligible(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "GET".equalsIgnoreCase(request.getMethod())
                && !uri.startsWith("/api/") && !uri.startsWith("/ws") && !uri.startsWith("/actuator")
                && !uri.contains(".");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, WebRequest request) {
        log.error("Unhandled exception processing {}", request.getDescription(false), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
