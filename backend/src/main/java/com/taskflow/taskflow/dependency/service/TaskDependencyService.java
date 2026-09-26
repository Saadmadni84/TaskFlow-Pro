package com.taskflow.taskflow.dependency.service;

import com.taskflow.taskflow.common.exception.DuplicateDependencyException;
import com.taskflow.taskflow.common.exception.InvalidDependencyException;
import com.taskflow.taskflow.common.exception.TaskNotFoundException;
import com.taskflow.taskflow.dependency.dto.CreateDependencyRequest;
import com.taskflow.taskflow.dependency.dto.DependencyMapper;
import com.taskflow.taskflow.dependency.dto.DependencyResponse;
import com.taskflow.taskflow.dependency.entity.TaskDependency;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaskDependencyService {

    private final TaskDependencyRepository dependencyRepository;
    private final TaskRepository taskRepository;

    public TaskDependencyService(TaskDependencyRepository dependencyRepository, TaskRepository taskRepository) {
        this.dependencyRepository = dependencyRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public DependencyResponse createDependency(CreateDependencyRequest request) {
        UUID predecessorId = request.predecessorTaskId();
        UUID successorId = request.successorTaskId();

        if (predecessorId == null || successorId == null) {
            throw new InvalidDependencyException("Predecessor and successor task IDs must not be null");
        }

        // Invariant 1: Self-dependency forbidden
        if (predecessorId.equals(successorId)) {
            throw new InvalidDependencyException("Self-dependency is forbidden: task cannot depend on itself");
        }

        // Invariant 2: Both tasks must exist
        Task predecessor = taskRepository.findById(predecessorId)
                .orElseThrow(() -> new TaskNotFoundException(predecessorId));
        Task successor = taskRepository.findById(successorId)
                .orElseThrow(() -> new TaskNotFoundException(successorId));

        // Invariant 3: Project isolation - tasks must belong to the exact same project
        UUID predProjectId = predecessor.getProject().getId();
        UUID succProjectId = successor.getProject().getId();
        if (!predProjectId.equals(succProjectId)) {
            throw new InvalidDependencyException(
                    String.format("Cross-project dependencies are forbidden: predecessor project [%s] does not match successor project [%s]",
                            predProjectId, succProjectId)
            );
        }

        // Invariant 4: No duplicate dependency edges
        if (dependencyRepository.existsByPredecessorIdAndSuccessorId(predecessorId, successorId)) {
            throw new DuplicateDependencyException(
                    String.format("Dependency edge already exists from task [%s] to task [%s]", predecessorId, successorId)
            );
        }

        TaskDependency dependency = new TaskDependency(predecessor, successor);
        TaskDependency saved = dependencyRepository.save(dependency);
        return DependencyMapper.toResponse(saved);
    }

    public List<DependencyResponse> getDependenciesByPredecessor(UUID predecessorTaskId) {
        return dependencyRepository.findByPredecessorId(predecessorTaskId).stream()
                .map(DependencyMapper::toResponse)
                .toList();
    }

    public List<DependencyResponse> getDependenciesBySuccessor(UUID successorTaskId) {
        return dependencyRepository.findBySuccessorId(successorTaskId).stream()
                .map(DependencyMapper::toResponse)
                .toList();
    }

    public List<DependencyResponse> getDependenciesByProject(UUID projectId) {
        return dependencyRepository.findByProjectId(projectId).stream()
                .map(DependencyMapper::toResponse)
                .toList();
    }

    @Transactional
    public void deleteDependency(UUID predecessorTaskId, UUID successorTaskId) {
        if (!dependencyRepository.existsByPredecessorIdAndSuccessorId(predecessorTaskId, successorTaskId)) {
            throw new InvalidDependencyException("Dependency relationship does not exist");
        }
        dependencyRepository.deleteByPredecessorIdAndSuccessorId(predecessorTaskId, successorTaskId);
    }
}
