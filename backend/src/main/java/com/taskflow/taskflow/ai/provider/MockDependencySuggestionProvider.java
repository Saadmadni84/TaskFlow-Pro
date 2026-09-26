package com.taskflow.taskflow.ai.provider;

import com.taskflow.taskflow.ai.model.DependencyCandidateTask;
import com.taskflow.taskflow.ai.model.DependencySuggestionContext;
import com.taskflow.taskflow.ai.model.DependencySuggestionResult;
import com.taskflow.taskflow.ai.model.RawDependencySuggestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Deterministic mock provider for local development, testing, and offline evaluation.
 * Uses semantic heuristics (software engineering lifecycle phases and dependency patterns)
 * to suggest realistic prerequisites without calling external LLM APIs.
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "mock")
public class MockDependencySuggestionProvider implements DependencySuggestionProvider {

    private static final Logger log = LoggerFactory.getLogger(MockDependencySuggestionProvider.class);

    @Override
    public DependencySuggestionResult suggest(DependencySuggestionContext context) {
        if (context == null || context.targetTask() == null || context.candidateTasks().isEmpty()) {
            return DependencySuggestionResult.empty();
        }

        DependencyCandidateTask target = context.targetTask();
        String targetTitle = target.title().toLowerCase(Locale.ROOT);
        String targetDesc = target.description() != null ? target.description().toLowerCase(Locale.ROOT) : "";
        int targetPhase = determinePhase(targetTitle, targetDesc);

        Set<String> existingEdgeKeys = context.existingDependencies().stream()
                .map(e -> e.predecessorId() + "->" + e.successorId())
                .collect(Collectors.toSet());

        List<RawDependencySuggestion> suggestions = new ArrayList<>();

        for (DependencyCandidateTask candidate : context.candidateTasks()) {
            if (candidate.id().equals(target.id())) {
                continue;
            }

            // Skip if already connected
            String edgeKey = candidate.id() + "->" + target.id();
            if (existingEdgeKeys.contains(edgeKey)) {
                continue;
            }

            String candTitle = candidate.title().toLowerCase(Locale.ROOT);
            String candDesc = candidate.description() != null ? candidate.description().toLowerCase(Locale.ROOT) : "";
            int candPhase = determinePhase(candTitle, candDesc);

            // Candidate is in an earlier lifecycle phase -> likely prerequisite for target
            if (candPhase > 0 && targetPhase > 0 && candPhase < targetPhase) {
                double confidence = calculateConfidence(candPhase, targetPhase);
                String reason = generateReason(candidate.title(), target.title(), candPhase, targetPhase);
                suggestions.add(new RawDependencySuggestion(candidate.id(), target.id(), confidence, reason));
            } else if (targetDesc.contains(candTitle) || (candidate.description() != null && candDesc.contains(targetTitle))) {
                // Semantic mention in descriptions
                suggestions.add(new RawDependencySuggestion(
                        candidate.id(),
                        target.id(),
                        0.80,
                        String.format("Task \"%s\" is referenced in the context of \"%s\".", candidate.title(), target.title())
                ));
            }

            if (suggestions.size() >= context.maxSuggestions()) {
                break;
            }
        }

        log.info("Mock AI suggestion provider produced {} candidate suggestions for task [{}]",
                suggestions.size(), target.id());

        return new DependencySuggestionResult(suggestions);
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    private int determinePhase(String title, String desc) {
        String combined = (title + " " + desc).toLowerCase(Locale.ROOT);

        // Phase 1: Database, Schema, Data Model, Architecture, Requirements
        if (combined.contains("database") || combined.contains("schema") || combined.contains("data model")
                || combined.contains("architecture") || combined.contains("migration") || combined.contains("requirements")
                || combined.contains("setup") || combined.contains("specification")) {
            return 1;
        }

        // Phase 2: Backend, API, REST, Controller, Service, Persistence, Repository, Endpoints
        if (combined.contains("api") || combined.contains("backend") || combined.contains("service")
                || combined.contains("controller") || combined.contains("endpoint") || combined.contains("server")
                || combined.contains("crud") || combined.contains("business logic")) {
            return 2;
        }

        // Phase 3: Frontend, UI, Component, View, Page, Client, Design System
        if (combined.contains("frontend") || combined.contains("ui") || combined.contains("client")
                || combined.contains("view") || combined.contains("component") || combined.contains("page")
                || combined.contains("react") || combined.contains("dashboard")) {
            return 3;
        }

        // Phase 4: Testing, QA, Integration Test, E2E, Verification
        if (combined.contains("test") || combined.contains("qa") || combined.contains("integration")
                || combined.contains("e2e") || combined.contains("validation") || combined.contains("verification")) {
            return 4;
        }

        // Phase 5: Deployment, Release, Production, CI/CD, Docker, Infra
        if (combined.contains("deploy") || combined.contains("release") || combined.contains("production")
                || combined.contains("docker") || combined.contains("ci/cd") || combined.contains("pipeline")
                || combined.contains("kubernetes")) {
            return 5;
        }

        return 0; // Unclassified
    }

    private double calculateConfidence(int candPhase, int targetPhase) {
        int diff = targetPhase - candPhase;
        if (diff == 1) {
            return 0.88; // Direct predecessor phase
        } else if (diff == 2) {
            return 0.82;
        } else {
            return 0.75;
        }
    }

    private String generateReason(String candTitle, String targetTitle, int candPhase, int targetPhase) {
        if (candPhase == 1 && targetPhase == 2) {
            return String.format("Backend APIs and services depend on the database schema and persistence layer defined in \"%s\".", candTitle);
        } else if (candPhase == 2 && targetPhase == 3) {
            return String.format("Frontend user interfaces rely on the backend API endpoints and data contracts provided by \"%s\".", candTitle);
        } else if (targetPhase == 4) {
            return String.format("Integration testing requires completion of the functional implementation in \"%s\" before test suites can execute.", candTitle);
        } else if (targetPhase == 5) {
            return String.format("Deployment and release workflows require prior completion and validation of \"%s\".", candTitle);
        } else {
            return String.format("Task \"%s\" logically precedes \"%s\" in the project lifecycle.", candTitle, targetTitle);
        }
    }
}
