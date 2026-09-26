package com.taskflow.taskflow.scheduling.controller;

import com.taskflow.taskflow.scheduling.dto.ScheduleImpactPreviewResponse;
import com.taskflow.taskflow.scheduling.dto.SchedulePreviewRequest;
import com.taskflow.taskflow.scheduling.service.ScheduleImpactPreviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller providing the dependency impact preview endpoint.
 *
 * Side-effect free simulation calculating the downstream impact of proposed schedule changes.
 */
@RestController
@RequestMapping("/api/tasks")
public class ScheduleImpactPreviewController {

    private final ScheduleImpactPreviewService previewService;

    public ScheduleImpactPreviewController(ScheduleImpactPreviewService previewService) {
        this.previewService = previewService;
    }

    /**
     * Previews the downstream schedule impact of a proposed planned start date for a task.
     *
     * @param taskId ID of the source task to evaluate
     * @param request proposed schedule change payload
     * @return 200 OK with the structured preview response, or 404/400 on error
     */
    @PostMapping("/{taskId}/schedule/preview")
    public ResponseEntity<ScheduleImpactPreviewResponse> previewScheduleImpact(
            @PathVariable UUID taskId,
            @Valid @RequestBody SchedulePreviewRequest request
    ) {
        ScheduleImpactPreviewResponse response = previewService.calculatePreview(taskId, request);
        return ResponseEntity.ok(response);
    }
}
