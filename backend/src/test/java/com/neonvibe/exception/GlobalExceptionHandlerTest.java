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

    @Test
    void accountNotAllowed_returnsForbidden() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleAccountNotAllowed(new AccountNotAllowedException("not allowed"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().get("error")).isEqualTo("forbidden");
    }

    @Test
    void missingRequestParameter_returnsBadRequest() {
        ResponseEntity<Map<String, Object>> response = handler.handleMalformedRequest(
                new org.springframework.web.bind.MissingServletRequestParameterException("track_id", "Long"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsKeys("error", "message", "timestamp");
        assertThat(response.getBody().get("error")).isEqualTo("bad_request");
    }

    @Test
    void typeMismatch_returnsBadRequest() {
        var ex = new org.springframework.web.method.annotation.MethodArgumentTypeMismatchException(
                "abc", Integer.class, "size", null, null);

        ResponseEntity<Map<String, Object>> response = handler.handleMalformedRequest(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().get("error")).isEqualTo("bad_request");
    }

    @Test
    void unreadableBody_returnsBadRequest() {
        ResponseEntity<Map<String, Object>> response = handler.handleMalformedRequest(
                new org.springframework.http.converter.HttpMessageNotReadableException("bad json"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().get("error")).isEqualTo("bad_request");
    }

    @Test
    void genericException_returns500() {
        ResponseEntity<Map<String, Object>> response = handler.handleGeneric(
                new RuntimeException("boom"),
                new org.springframework.web.context.request.ServletWebRequest(
                        new org.springframework.mock.web.MockHttpServletRequest()));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().get("error")).isEqualTo("internal_error");
    }

    @Test
    void unknownSpaRoute_forwardsToIndexHtml() throws Exception {
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("GET", "/library");
        request.setRequestURI("/library");
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();

        Object result = handler.handleNoResource(
                new org.springframework.web.servlet.resource.NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "/library"),
                request, response);

        assertThat(result).isNull();
        assertThat(response.getForwardedUrl()).isEqualTo("/index.html");
    }

    @Test
    void unknownAssetRoute_returns404WithoutForwarding() throws Exception {
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("GET", "/assets/app.js");
        request.setRequestURI("/assets/app.js");
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();

        Object result = handler.handleNoResource(
                new org.springframework.web.servlet.resource.NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "/assets/app.js"),
                request, response);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        assertThat(((ResponseEntity<?>) result).getStatusCode().value()).isEqualTo(404);
        assertThat(response.getForwardedUrl()).isNull();
    }

    @Test
    void unknownApiRoute_returns404WithoutForwarding() throws Exception {
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/nope");
        request.setRequestURI("/api/v1/nope");
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();

        Object result = handler.handleNoResource(
                new org.springframework.web.servlet.resource.NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "/api/v1/nope"),
                request, response);

        assertThat(((ResponseEntity<?>) result).getStatusCode().value()).isEqualTo(404);
        assertThat(response.getForwardedUrl()).isNull();
    }
}
