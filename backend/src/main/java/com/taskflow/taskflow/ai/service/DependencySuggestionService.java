package com.taskflow.taskflow.ai.service;

import com.taskflow.taskflow.ai.config.AiProperties;
import com.taskflow.taskflow.ai.dto.AcceptSuggestionRequest;
import com.taskflow.taskflow.ai.dto.DependencySuggestionDto;
import com.taskflow.taskflow.ai.dto.DependencySuggestionResponse;
import com.taskflow.taskflow.ai.dto.TaskSummaryDto;
import com.taskflow.taskflow.ai.exception.AiDisabledException;
import com.taskflow.taskflow.ai.model.DependencyCandidateTask;
import com.taskflow.taskflow.ai.model.DependencySuggestionContext;
import com.taskflow.taskflow.ai.model.DependencySuggestionResult;
import com.taskflow.taskflow.ai.model.RawDependencySuggestion;
import com.taskflow.taskflow.ai.provider.DependencySuggestionProvider;
import com.taskflow.taskflow.common.exception.TaskNotFoundException;
import com.taskflow.taskflow.dependency.dto.CreateDependencyRequest;
import com.taskflow.taskflow.dependency.dto.DependencyResponse;
import com.taskflow.taskflow.dependency.entity.TaskDependency;
import com.taskflow.taskflow.dependency.graph.DependencyGraph;
import com.taskflow.taskflow.dependency.graph.DependencyGraphBuilder;
import com.taskflow.taskflow.dependency.graph.GraphTraversalService;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.dependency.service.TaskDependencyService;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service orchestrating AI-assisted dependency suggestion generation and human acceptance.
 *
 * Guarantees:
 * 1. AI is an assistant: suggestions never directly mutate the database or DAG.
 * 2. Grounded context: only tasks from the exact same project are exposed to the model.
 * 3. Server-side validation: filters unknown IDs, cross-project tasks, self-dependencies, duplicates, and cycles.
 * 4. Human acceptance: accepted suggestions are validated by the authoritative deterministic dependency service.
 */
@Service
@Transactional(readOnly = true)
public class DependencySuggestionService {

    private static final Logger log = LoggerFactory.getLogger(DependencySuggestionService.class);
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private final AiProperties aiProperties;
    private final DependencySuggestionProvider provider;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository dependencyRepository;
    private final TaskDependencyService taskDependencyService;
    private final DependencyGraphBuilder graphBuilder;
    private final GraphTraversalService traversalService;
    private final com.taskflow.taskflow.common.metrics.TaskFlowMetrics metrics;

    @org.springframework.beans.factory.annotation.Autowired
    public DependencySuggestionService(
            AiProperties aiProperties,
            DependencySuggestionProvider provider,
            TaskRepository taskRepository,
            TaskDependencyRepository dependencyRepository,
            TaskDependencyService taskDependencyService,
            DependencyGraphBuilder graphBuilder,
            GraphTraversalService traversalService,
            com.taskflow.taskflow.common.metrics.TaskFlowMetrics metrics
    ) {
        this.aiProperties = aiProperties;
        this.provider = provider;
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
        this.taskDependencyService = taskDependencyService;
        this.graphBuilder = graphBuilder;
        this.traversalService = traversalService;
        this.metrics = metrics != null ? metrics : new com.taskflow.taskflow.common.metrics.TaskFlowMetrics(null);
    }

    public DependencySuggestionService(
            AiProperties aiProperties,
            DependencySuggestionProvider provider,
            TaskRepository taskRepository,
            TaskDependencyRepository dependencyRepository,
            TaskDependencyService taskDependencyService,
            DependencyGraphBuilder graphBuilder,
            GraphTraversalService traversalService
    ) {
        this(aiProperties, provider, taskRepository, dependencyRepository, taskDependencyService,
                graphBuilder, traversalService, new com.taskflow.taskflow.common.metrics.TaskFlowMetrics(null));
    }

    /**
     * Analyzes project context and queries the configured AI provider to propose candidate dependencies
     * for human review. Does NOT mutate the database.
     *
     * @param targetTaskId the ID of the task to find candidate dependencies for
     * @param requestedLimit optional client requested maximum number of suggestions
     * @return filtered, grounded dependency suggestions
     */
    public DependencySuggestionResponse generateSuggestions(UUID targetTaskId, Integer requestedLimit) {
        long startTime = System.currentTimeMillis();
        Objects.requireNonNull(targetTaskId, "Target task ID must not be null");

        if (!aiProperties.isEnabled()) {
            log.warn("operation=AI_DEPENDENCY_SUGGESTION targetTaskId={} result=DISABLED", targetTaskId);
            metrics.recordAiSuggestion(System.currentTimeMillis() - startTime, "disabled");
            throw new AiDisabledException();
        }

        Task targetTask = taskRepository.findById(targetTaskId)
                .orElseThrow(() -> new TaskNotFoundException(targetTaskId));

        UUID projectId = targetTask.getProject().getId();

        // 1. Single-batch load all tasks for the project to eliminate N+1 queries
        Map<UUID, Task> projectTasksMap = taskRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));

        // Filter candidate tasks (exclude target task itself)
        List<DependencyCandidateTask> candidateTasks = projectTasksMap.values().stream()
                .filter(t -> !t.getId().equals(targetTaskId))
                .map(t -> new DependencyCandidateTask(
                        t.getId(),
                        t.getTitle(),
                        truncateDescription(t.getDescription()),
                        t.getWorkflowStatus() != null ? t.getWorkflowStatus().name() : "BACKLOG"
                ))
                .toList();

        if (candidateTasks.isEmpty()) {
            log.info("No other tasks exist in project [{}] to suggest dependencies for task [{}]", projectId, targetTaskId);
            return new DependencySuggestionResponse(targetTaskId, 0, List.of());
        }

        // 2. Load existing dependencies for the project
        List<TaskDependency> existingList = dependencyRepository.findByProjectId(projectId);
        Set<String> existingEdgeKeys = new HashSet<>();
        List<DependencySuggestionContext.ExistingEdge> existingEdges = new ArrayList<>();
        for (TaskDependency dep : existingList) {
            UUID predId = dep.getPredecessor().getId();
            UUID succId = dep.getSuccessor().getId();
            existingEdgeKeys.add(edgeKey(predId, succId));
            existingEdges.add(new DependencySuggestionContext.ExistingEdge(predId, succId));
        }

        int maxLimit = calculateEffectiveLimit(requestedLimit);

        DependencyCandidateTask targetDto = new DependencyCandidateTask(
                targetTask.getId(),
                targetTask.getTitle(),
                truncateDescription(targetTask.getDescription()),
                targetTask.getWorkflowStatus() != null ? targetTask.getWorkflowStatus().name() : "BACKLOG"
        );

        DependencySuggestionContext context = new DependencySuggestionContext(
                targetDto,
                candidateTasks,
                existingEdges,
                maxLimit
        );

        log.info("Requesting AI dependency suggestions for task [{}] in project [{}] (provider={}, candidates={})",
                targetTaskId, projectId, provider.getProviderName(), candidateTasks.size());

        DependencySuggestionResult rawResult = provider.suggest(context);
        if (rawResult == null || rawResult.suggestions().isEmpty()) {
            log.info("AI provider returned 0 suggestions for task [{}]", targetTaskId);
            return new DependencySuggestionResponse(targetTaskId, 0, List.of());
        }

        // 3. Build current graph for server-side cycle pre-filtering
        DependencyGraph currentGraph = graphBuilder.buildGraphForProject(projectId);

        // 4. Server-side validation and filtering
        List<DependencySuggestionDto> validatedSuggestions = new ArrayList<>();
        Set<String> seenProposedEdges = new HashSet<>();

        for (RawDependencySuggestion raw : rawResult.suggestions()) {
            if (raw == null) {
                continue;
            }

            UUID predId = raw.predecessorTaskId();
            UUID succId = raw.successorTaskId();

            // Validate non-null IDs
            if (predId == null || succId == null) {
                log.debug("Filtered suggestion with null task ID: pred={}, succ={}", predId, succId);
                continue;
            }

            // Hallucination & Cross-project defense: Both tasks must belong to this exact project
            if (!projectTasksMap.containsKey(predId) || !projectTasksMap.containsKey(succId)) {
                log.warn("Filtered suggestion referencing unknown or cross-project task: pred={}, succ={}", predId, succId);
                continue;
            }

            // Self-dependency defense
            if (predId.equals(succId)) {
                log.debug("Filtered self-dependency suggestion: {}", predId);
                continue;
            }

            // Duplicate defense: Edge already exists in database
            String key = edgeKey(predId, succId);
            if (existingEdgeKeys.contains(key)) {
                log.debug("Filtered duplicate suggestion already existing in project: {}", key);
                continue;
            }

            // De-duplicate within AI response itself
            if (!seenProposedEdges.add(key)) {
                continue;
            }

            // Confidence validation
            if (raw.confidence() == null || raw.confidence() < 0.0 || raw.confidence() > 1.0) {
                log.debug("Filtered suggestion with invalid confidence: {}", raw.confidence());
                continue;
            }

            // Reason validation
            if (raw.reason() == null || raw.reason().isBlank()) {
                log.debug("Filtered suggestion with blank reason");
                continue;
            }

            // Cycle prevention: If adding predId -> succId creates a cycle (succId is already an ancestor of predId)
            if (traversalService.isReachable(currentGraph, succId, predId)) {
                log.info("Filtered suggestion [{} -> {}] because it would close a cycle in project [{}]",
                        predId, succId, projectId);
                continue;
            }

            Task predTask = projectTasksMap.get(predId);
            Task succTask = projectTasksMap.get(succId);

            validatedSuggestions.add(new DependencySuggestionDto(
                    new TaskSummaryDto(predTask.getId(), predTask.getTitle()),
                    new TaskSummaryDto(succTask.getId(), succTask.getTitle()),
                    raw.confidence(),
                    raw.reason().trim()
            ));

            if (validatedSuggestions.size() >= maxLimit) {
                break;
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("operation=AI_DEPENDENCY_SUGGESTION projectId={} targetTaskId={} candidateCount={} provider={} result=SUCCESS suggestionCount={} durationMs={}",
                projectId, targetTaskId, candidateTasks.size(), provider.getProviderName(), validatedSuggestions.size(), durationMs);
        metrics.recordAiSuggestion(durationMs, "success");

        return new DependencySuggestionResponse(targetTaskId, validatedSuggestions.size(), validatedSuggestions);
    }

    /**
     * Explicit human acceptance of an AI-suggested dependency edge.
     * Passes the proposed relationship directly into the authoritative deterministic dependency engine.
     *
     * @param request the accepted relationship
     * @return created dependency response
     */
    @Transactional
    public DependencyResponse acceptSuggestion(AcceptSuggestionRequest request) {
        Objects.requireNonNull(request, "Accept suggestion request must not be null");

        log.info("User explicitly accepted AI dependency suggestion: predecessor [{}] -> successor [{}]",
                request.predecessorTaskId(), request.successorTaskId());

        // Delegate to authoritative deterministic dependency service (cycle checks, persistence, readiness, schedule)
        return taskDependencyService.createDependency(
                new CreateDependencyRequest(request.predecessorTaskId(), request.successorTaskId())
        );
    }

    private int calculateEffectiveLimit(Integer requestedLimit) {
        int configuredMax = aiProperties.getMaxSuggestions() > 0 ? aiProperties.getMaxSuggestions() : 10;
        if (requestedLimit == null || requestedLimit <= 0) {
            return configuredMax;
        }
        return Math.min(requestedLimit, configuredMax);
    }

    private String truncateDescription(String description) {
        if (description == null) {
            return null;
        }
        if (description.length() <= MAX_DESCRIPTION_LENGTH) {
            return description;
        }
        return description.substring(0, MAX_DESCRIPTION_LENGTH) + "...";
    }

    private String edgeKey(UUID predecessorId, UUID successorId) {
        return predecessorId.toString() + "->" + successorId.toString();
    }
}
