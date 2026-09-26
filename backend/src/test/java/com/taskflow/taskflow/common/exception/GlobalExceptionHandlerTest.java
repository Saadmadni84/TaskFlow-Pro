package com.taskflow.taskflow.common.exception;

import com.taskflow.taskflow.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("GET", "/api/tasks/123");
    }

    @Test
    void shouldHandleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Task", "123");

        ResponseEntity<ErrorResponse> responseEntity = exceptionHandler.handleApiException(ex, request);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ErrorResponse body = responseEntity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(body.message()).isEqualTo("Task not found with identifier: 123");
        assertThat(body.path()).isEqualTo("/api/tasks/123");
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    void shouldHandleDomainException() {
        DomainException ex = new DomainException("Cycle detected between task 1 and 2", "CYCLE_DETECTED");

        ResponseEntity<ErrorResponse> responseEntity = exceptionHandler.handleApiException(ex, request);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ErrorResponse body = responseEntity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(422);
        assertThat(body.code()).isEqualTo("CYCLE_DETECTED");
        assertThat(body.message()).isEqualTo("Cycle detected between task 1 and 2");
    }

    @Test
    void shouldHandleUnhandledExceptionWithoutExposingInternalDetails() {
        Exception ex = new RuntimeException("Database connection timeout or internal pointer error");

        ResponseEntity<ErrorResponse> responseEntity = exceptionHandler.handleUnhandledException(ex, request);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse body = responseEntity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(500);
        assertThat(body.code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(body.message()).isEqualTo("An unexpected internal server error occurred");
        assertThat(body.path()).isEqualTo("/api/tasks/123");
        assertThat(body.timestamp()).isNotNull();
    }
}
