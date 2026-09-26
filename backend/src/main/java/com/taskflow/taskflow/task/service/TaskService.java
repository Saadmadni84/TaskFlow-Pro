package com.taskflow.taskflow.task.service;

import com.taskflow.taskflow.common.exception.ProjectNotFoundException;
import com.taskflow.taskflow.common.exception.TaskNotFoundException;
import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import com.taskflow.taskflow.task.dto.CreateTaskRequest;
import com.taskflow.taskflow.task.dto.TaskMapper;
import com.taskflow.taskflow.task.dto.TaskResponse;
import com.taskflow.taskflow.task.dto.UpdateTaskRequest;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public TaskResponse createTask(CreateTaskRequest request) {
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ProjectNotFoundException(request.projectId()));

        Task task = TaskMapper.toEntity(request, project);
        Task saved = taskRepository.save(task);
        return TaskMapper.toResponse(saved);
    }

    public Task getTaskEntity(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    public TaskResponse getTask(UUID id) {
        return TaskMapper.toResponse(getTaskEntity(id));
    }

    public List<TaskResponse> getTasksByProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
        return taskRepository.findByProjectId(projectId).stream()
                .map(TaskMapper::toResponse)
                .toList();
    }

    @Transactional
    public TaskResponse updateTask(UUID id, UpdateTaskRequest request) {
        Task task = getTaskEntity(id);
        task.setTitle(request.title());
        task.setDescription(request.description());
        if (request.workflowStatus() != null) {
            task.setWorkflowStatus(request.workflowStatus());
        }
        task.setDatesAndDuration(request.startDate(), request.dueDate(), request.durationDays());
        return TaskMapper.toResponse(task);
    }

    @Transactional
    public void deleteTask(UUID id) {
        if (!taskRepository.existsById(id)) {
            throw new TaskNotFoundException(id);
        }
        taskRepository.deleteById(id);
    }
}
