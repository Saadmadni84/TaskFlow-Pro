package com.taskflow.taskflow.ai.controller;

import com.taskflow.taskflow.ai.dto.AcceptSuggestionRequest;
import com.taskflow.taskflow.ai.dto.DependencySuggestionResponse;
import com.taskflow.taskflow.ai.service.DependencySuggestionService;
import com.taskflow.taskflow.dependency.dto.DependencyResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for AI-assisted dependency suggestions and explicit human acceptance.
 */
@RestController
@RequestMapping("/api")
public class DependencySuggestionController {

    private final DependencySuggestionService suggestionService;

    public DependencySuggestionController(DependencySuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    /**
     * Generates candidate dependency suggestions for the given target task using an LLM.
     * AI is purely an assistance layer; this endpoint NEVER mutates the graph or database.
     *
     * @param taskId target task to analyze
     * @param limit optional maximum number of suggestions to return
     * @return candidate dependency suggestions
     */
    @PostMapping("/tasks/{taskId}/dependency-suggestions")
    public ResponseEntity<DependencySuggestionResponse> generateSuggestions(
            @PathVariable UUID taskId,
            @RequestParam(required = false) Integer limit
    ) {
        DependencySuggestionResponse response = suggestionService.generateSuggestions(taskId, limit);
        return ResponseEntity.ok(response);
    }

    /**
     * Explicit human acceptance of an AI-suggested dependency edge.
     * Routes the proposed relationship directly into the authoritative deterministic dependency engine.
     *
     * @param request accepted predecessor -> successor pair
     * @return created dependency response
     */
    @PostMapping("/dependency-suggestions/accept")
    public ResponseEntity<DependencyResponse> acceptSuggestion(
            @Valid @RequestBody AcceptSuggestionRequest request
    ) {
        DependencyResponse response = suggestionService.acceptSuggestion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
