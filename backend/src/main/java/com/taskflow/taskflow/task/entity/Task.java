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
import java.time.temporal.ChronoUnit;
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

    @Column(name = "planned_start_date")
    private LocalDate plannedStartDate;

    @Column(name = "scheduled_start_date")
    private LocalDate scheduledStartDate;

    @Column(name = "scheduled_due_date")
    private LocalDate scheduledDueDate;

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
        if (scheduledStartDate != null && scheduledDueDate != null && scheduledStartDate.isAfter(scheduledDueDate)) {
            throw new IllegalArgumentException("Scheduled start date (" + scheduledStartDate + ") cannot be after scheduled due date (" + scheduledDueDate + ")");
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

        if (startDate != null) {
            this.plannedStartDate = startDate;
            if (durationDays != null && durationDays > 0) {
                this.durationDays = durationDays;
            } else if (dueDate != null) {
                this.durationDays = (int) ChronoUnit.DAYS.between(startDate, dueDate) + 1;
            } else {
                this.durationDays = 1;
            }
            this.scheduledStartDate = startDate;
            this.scheduledDueDate = dueDate != null ? dueDate : startDate.plusDays(this.durationDays - 1);
            this.startDate = this.scheduledStartDate;
            this.dueDate = this.scheduledDueDate;
        } else {
            this.plannedStartDate = null;
            this.scheduledStartDate = null;
            this.scheduledDueDate = null;
            this.startDate = null;
            this.dueDate = dueDate;
            this.durationDays = durationDays;
        }
    }

    /**
     * Updates the user's independent planned schedule.
     */
    public void setPlannedSchedule(LocalDate plannedStartDate, Integer durationDays) {
        if (durationDays != null && durationDays < 1) {
            throw new IllegalArgumentException("Duration days must be at least 1, but was: " + durationDays);
        }
        this.plannedStartDate = plannedStartDate;
        if (durationDays != null) {
            this.durationDays = durationDays;
        } else if (this.durationDays == null || this.durationDays < 1) {
            this.durationDays = 1;
        }

        if (this.plannedStartDate != null) {
            this.scheduledStartDate = this.plannedStartDate;
            this.scheduledDueDate = this.plannedStartDate.plusDays(this.durationDays - 1);
            this.startDate = this.scheduledStartDate;
            this.dueDate = this.scheduledDueDate;
        }
    }

    /**
     * Updates the calculated scheduled dates from the dependency-aware scheduling engine.
     */
    public void setScheduledDates(LocalDate scheduledStartDate, LocalDate scheduledDueDate) {
        if (scheduledStartDate != null && scheduledDueDate != null && scheduledStartDate.isAfter(scheduledDueDate)) {
            throw new IllegalArgumentException("Scheduled start date (" + scheduledStartDate + ") cannot be after scheduled due date (" + scheduledDueDate + ")");
        }
        this.scheduledStartDate = scheduledStartDate;
        this.scheduledDueDate = scheduledDueDate;
        this.startDate = scheduledStartDate;
        this.dueDate = scheduledDueDate;
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

    public void setDependencyStatus(DependencyStatus dependencyStatus) {
        this.dependencyStatus = Objects.requireNonNull(dependencyStatus, "Dependency status must not be null");
    }

    public LocalDate getPlannedStartDate() {
        return plannedStartDate;
    }

    public void setPlannedStartDate(LocalDate plannedStartDate) {
        this.plannedStartDate = plannedStartDate;
    }

    public LocalDate getScheduledStartDate() {
        return scheduledStartDate;
    }

    public void setScheduledStartDate(LocalDate scheduledStartDate) {
        this.scheduledStartDate = scheduledStartDate;
        this.startDate = scheduledStartDate;
    }

    public LocalDate getScheduledDueDate() {
        return scheduledDueDate;
    }

    public void setScheduledDueDate(LocalDate scheduledDueDate) {
        this.scheduledDueDate = scheduledDueDate;
        this.dueDate = scheduledDueDate;
    }

    public LocalDate getStartDate() {
        return startDate != null ? startDate : scheduledStartDate;
    }

    public LocalDate getDueDate() {
        return dueDate != null ? dueDate : scheduledDueDate;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(Integer durationDays) {
        this.durationDays = durationDays;
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
