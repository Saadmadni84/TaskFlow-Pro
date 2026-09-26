package com.taskflow.taskflow.project.service;

import com.taskflow.taskflow.common.exception.ProjectNotFoundException;
import com.taskflow.taskflow.project.dto.CreateProjectRequest;
import com.taskflow.taskflow.project.dto.ProjectMapper;
import com.taskflow.taskflow.project.dto.ProjectResponse;
import com.taskflow.taskflow.project.dto.UpdateProjectRequest;
import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        Project project = ProjectMapper.toEntity(request);
        Project saved = projectRepository.save(project);
        return ProjectMapper.toResponse(saved);
    }

    public Project getProjectEntity(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));
    }

    public ProjectResponse getProject(UUID id) {
        return ProjectMapper.toResponse(getProjectEntity(id));
    }

    public List<ProjectResponse> getAllProjects() {
        return projectRepository.findAll().stream()
                .map(ProjectMapper::toResponse)
                .toList();
    }

    @Transactional
    public ProjectResponse updateProject(UUID id, UpdateProjectRequest request) {
        Project project = getProjectEntity(id);
        project.setName(request.name());
        project.setDescription(request.description());
        return ProjectMapper.toResponse(project);
    }

    @Transactional
    public void deleteProject(UUID id) {
        if (!projectRepository.existsById(id)) {
            throw new ProjectNotFoundException(id);
        }
        projectRepository.deleteById(id);
    }
}
