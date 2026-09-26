package com.taskflow.taskflow.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class CycleDetectedException extends ApiException {

    private final List<UUID> cyclePath;

    public CycleDetectedException(String message) {
        this(message, Collections.emptyList());
    }

    public CycleDetectedException(String message, List<UUID> cyclePath) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "CYCLE_DETECTED");
        this.cyclePath = cyclePath != null ? List.copyOf(cyclePath) : Collections.emptyList();
    }

    public List<UUID> getCyclePath() {
        return cyclePath;
    }
}
