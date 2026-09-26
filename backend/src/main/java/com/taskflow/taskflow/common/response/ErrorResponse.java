package com.taskflow.taskflow.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<ValidationErrorDetail> details
) {
    public ErrorResponse(int status, String code, String message, String path) {
        this(Instant.now(), status, code, message, path, null);
    }

    public ErrorResponse(int status, String code, String message, String path, List<ValidationErrorDetail> details) {
        this(Instant.now(), status, code, message, path, details);
    }

    public record ValidationErrorDetail(String field, String message) {}
}
