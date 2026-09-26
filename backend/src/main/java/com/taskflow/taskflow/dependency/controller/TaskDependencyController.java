package com.taskflow.taskflow.dependency.controller;

import com.taskflow.taskflow.dependency.dto.CreateDependencyRequest;
import com.taskflow.taskflow.dependency.dto.DependencyResponse;
import com.taskflow.taskflow.dependency.graph.AffectedSubgraph;
import com.taskflow.taskflow.dependency.service.TaskDependencyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller managing task dependency relationships and graph queries.
 */
@RestController
@RequestMapping("/api/dependencies")
public class TaskDependencyController {

    private final TaskDependencyService dependencyService;

    public TaskDependencyController(TaskDependencyService dependencyService) {
        this.dependencyService = dependencyService;
    }

    @PostMapping
    public ResponseEntity<DependencyResponse> createDependency(
            @Valid @RequestBody CreateDependencyRequest request
    ) {
        DependencyResponse response = dependencyService.createDependency(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<DependencyResponse>> getDependenciesByProject(
            @PathVariable UUID projectId
    ) {
        return ResponseEntity.ok(dependencyService.getDependenciesByProject(projectId));
    }

    @GetMapping("/predecessor/{taskId}")
    public ResponseEntity<List<DependencyResponse>> getDependenciesByPredecessor(
            @PathVariable UUID taskId
    ) {
        return ResponseEntity.ok(dependencyService.getDependenciesByPredecessor(taskId));
    }

    @GetMapping("/successor/{taskId}")
    public ResponseEntity<List<DependencyResponse>> getDependenciesBySuccessor(
            @PathVariable UUID taskId
    ) {
        return ResponseEntity.ok(dependencyService.getDependenciesBySuccessor(taskId));
    }

    @GetMapping("/affected-subgraph/{taskId}")
    public ResponseEntity<AffectedSubgraph> getAffectedSubgraph(
            @PathVariable UUID taskId
    ) {
        return ResponseEntity.ok(dependencyService.getAffectedSubgraph(taskId));
    }

    @DeleteMapping("/{predecessorTaskId}/{successorTaskId}")
    public ResponseEntity<Void> deleteDependency(
            @PathVariable UUID predecessorTaskId,
            @PathVariable UUID successorTaskId
    ) {
        dependencyService.deleteDependency(predecessorTaskId, successorTaskId);
        return ResponseEntity.noContent().build();
    }
}
