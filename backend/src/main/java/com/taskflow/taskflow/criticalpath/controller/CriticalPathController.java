package com.taskflow.taskflow.criticalpath.controller;

import com.taskflow.taskflow.criticalpath.dto.CriticalPathResponse;
import com.taskflow.taskflow.criticalpath.service.CriticalPathService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller exposing project Critical Path Analysis.
 */
@RestController
@RequestMapping("/api/projects")
public class CriticalPathController {

    private final CriticalPathService criticalPathService;

    public CriticalPathController(CriticalPathService criticalPathService) {
        this.criticalPathService = criticalPathService;
    }

    /**
     * Retrieves the Critical Path Analysis for the specified project.
     * Purely analytical and read-only; performs zero mutations on the database.
     *
     * @param projectId UUID of the project
     * @return project completion date, critical tasks, critical paths, and individual task metrics
     */
    @GetMapping("/{projectId}/critical-path")
    public ResponseEntity<CriticalPathResponse> getCriticalPath(@PathVariable UUID projectId) {
        CriticalPathResponse response = criticalPathService.calculateCriticalPath(projectId);
        return ResponseEntity.ok(response);
    }
}
