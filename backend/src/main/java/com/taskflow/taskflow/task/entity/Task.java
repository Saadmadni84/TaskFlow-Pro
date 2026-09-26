package com.taskflow.taskflow.task.entity;

import com.taskflow.taskflow.project.entity.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_status", nullable = false, length = 32)
    private TaskStatus workflowStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "dependency_status", nullable = false, length = 32)
    private DependencyStatus dependencyStatus;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Task() {
        // Required by JPA
    }

    public Task(
            UUID id,
            Project project,
            String title,
            String description,
            TaskStatus workflowStatus,
            DependencyStatus dependencyStatus,
            LocalDate startDate,
            LocalDate dueDate,
            Integer durationDays
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.project = Objects.requireNonNull(project, "Project must not be null");
        setTitle(title);
        this.description = description;
        this.workflowStatus = workflowStatus != null ? workflowStatus : TaskStatus.BACKLOG;
        this.dependencyStatus = dependencyStatus != null ? dependencyStatus : DependencyStatus.READY;
        setDatesAndDuration(startDate, dueDate, durationDays);
    }

    public Task(
            Project project,
            String title,
            String description,
            TaskStatus workflowStatus,
            LocalDate startDate,
            LocalDate dueDate,
            Integer durationDays
    ) {
        this(UUID.randomUUID(), project, title, description, workflowStatus, DependencyStatus.READY, startDate, dueDate, durationDays);
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.workflowStatus == null) {
            this.workflowStatus = TaskStatus.BACKLOG;
        }
        if (this.dependencyStatus == null) {
            this.dependencyStatus = DependencyStatus.READY;
        }
        validateInvariants();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
        validateInvariants();
    }

    public void validateInvariants() {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Task title must not be blank");
        }
        if (startDate != null && dueDate != null && startDate.isAfter(dueDate)) {
            throw new IllegalArgumentException("Start date (" + startDate + ") cannot be after due date (" + dueDate + ")");
        }
        if (durationDays != null && durationDays < 0) {
            throw new IllegalArgumentException("Duration days cannot be negative: " + durationDays);
        }
        if (project == null) {
            throw new IllegalArgumentException("Task must be associated with a valid Project");
        }
    }

    public void setDatesAndDuration(LocalDate startDate, LocalDate dueDate, Integer durationDays) {
        if (startDate != null && dueDate != null && startDate.isAfter(dueDate)) {
            throw new IllegalArgumentException("Start date (" + startDate + ") cannot be after due date (" + dueDate + ")");
        }
        if (durationDays != null && durationDays < 0) {
            throw new IllegalArgumentException("Duration days cannot be negative: " + durationDays);
        }
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.durationDays = durationDays;
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = Objects.requireNonNull(project, "Project must not be null");
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Task title must not be blank");
        }
        this.title = title.trim();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskStatus getWorkflowStatus() {
        return workflowStatus;
    }

    public void setWorkflowStatus(TaskStatus workflowStatus) {
        this.workflowStatus = Objects.requireNonNull(workflowStatus, "Workflow status must not be null");
    }

    public DependencyStatus getDependencyStatus() {
        return dependencyStatus;
    }

    /**
     * Package-private or engine-level setter. Dependency status is derived by the DAG engine,
     * not client-controlled.
     */
    public void setDependencyStatus(DependencyStatus dependencyStatus) {
        this.dependencyStatus = Objects.requireNonNull(dependencyStatus, "Dependency status must not be null");
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Task task)) return false;
        return Objects.equals(id, task.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
