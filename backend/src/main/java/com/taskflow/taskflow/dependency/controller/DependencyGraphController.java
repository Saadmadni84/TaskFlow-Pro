package com.taskflow.taskflow.dependency.controller;

import com.taskflow.taskflow.dependency.dto.DependencyGraphDto;
import com.taskflow.taskflow.dependency.service.DependencyGraphQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller exposing project-scoped visual dependency graph projections.
 */
@RestController
@RequestMapping("/api/projects")
public class DependencyGraphController {

    private final DependencyGraphQueryService queryService;

    public DependencyGraphController(DependencyGraphQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Retrieves the visual dependency graph projection for a project.
     * Purely read-only; performs zero mutations on graph state, tasks, or schedules.
     *
     * @param projectId UUID of the project
     * @return project-scoped nodes and directed edges
     */
    @GetMapping("/{projectId}/dependency-graph")
    public ResponseEntity<DependencyGraphDto> getDependencyGraph(@PathVariable UUID projectId) {
        DependencyGraphDto graph = queryService.getProjectDependencyGraph(projectId);
        return ResponseEntity.ok(graph);
    }
}
