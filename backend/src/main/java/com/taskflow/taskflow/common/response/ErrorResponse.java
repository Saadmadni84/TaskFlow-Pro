package com.taskflow.taskflow.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.MDC;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId,
        List<ValidationErrorDetail> details
) {
    public ErrorResponse(int status, String code, String message, String path) {
        this(Instant.now(), status, code, message, path, MDC.get("requestId"), null);
    }

    public ErrorResponse(int status, String code, String message, String path, List<ValidationErrorDetail> details) {
        this(Instant.now(), status, code, message, path, MDC.get("requestId"), details);
    }

    public ErrorResponse(int status, String code, String message, String path, String requestId, List<ValidationErrorDetail> details) {
        this(Instant.now(), status, code, message, path, requestId != null ? requestId : MDC.get("requestId"), details);
    }

    public record ValidationErrorDetail(String field, String message) {}
}
