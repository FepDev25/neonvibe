package com.neonvibe.exception;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the global exception handler produces the standard error contract:
 * {@code {error, message, timestamp}}.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void resourceNotFound_returnsExpectedContract() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleNotFound(new ResourceNotFoundException("track not found"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsKeys("error", "message", "timestamp");
        assertThat(body.get("error")).isEqualTo("not_found");
        assertThat(body.get("message")).isEqualTo("track not found");
        assertThat(body.get("timestamp")).isNotNull();
    }

    @Test
    void validationError_returnsBadRequest() {
        org.springframework.validation.BindingResult bindingResult =
                new org.springframework.validation.BeanPropertyBindingResult(null, "obj");
        bindingResult.addError(new org.springframework.validation.FieldError(
                "obj", "name", "must not be blank"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsKeys("error", "message", "timestamp");
        assertThat(response.getBody().get("error")).isEqualTo("validation_error");
        assertThat(response.getBody().get("message").toString())
                .contains("name");
    }

    @Test
    void invalidToken_returnsUnauthorized() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleInvalidToken(new InvalidTokenException("expired token"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().get("error")).isEqualTo("unauthorized");
        assertThat(response.getBody().get("message")).isEqualTo("expired token");
    }

    @Test
    void methodNotSupported_returns405() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleMethodNotSupported(
                        new HttpRequestMethodNotSupportedException("GET"));

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody().get("error")).isEqualTo("method_not_allowed");
    }
}
