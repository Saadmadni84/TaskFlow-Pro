package com.taskflow.taskflow.dependency.entity;

import com.taskflow.taskflow.task.entity.Task;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Directed dependency edge between two tasks:
 * predecessor -> successor
 *
 * Meaning: successor depends on predecessor.
 * Predecessor must be completed before successor is eligible to be READY.
 */
@Entity
@Table(
        name = "task_dependencies",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_task_dependencies",
                        columnNames = {"predecessor_task_id", "successor_task_id"}
                )
        }
)
public class TaskDependency {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "predecessor_task_id", nullable = false)
    private Task predecessor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "successor_task_id", nullable = false)
    private Task successor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskDependency() {
        // Required by JPA
    }

    public TaskDependency(UUID id, Task predecessor, Task successor) {
        this.id = id != null ? id : UUID.randomUUID();
        setPredecessorAndSuccessor(predecessor, successor);
    }

    public TaskDependency(Task predecessor, Task successor) {
        this(UUID.randomUUID(), predecessor, successor);
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        validateInvariants();
    }

    public void validateInvariants() {
        if (predecessor == null || successor == null) {
            throw new IllegalArgumentException("Both predecessor and successor tasks are required");
        }
        if (predecessor.getId() != null && successor.getId() != null && predecessor.getId().equals(successor.getId())) {
            throw new IllegalArgumentException("Self-dependency is forbidden: task cannot depend on itself");
        }
    }

    public void setPredecessorAndSuccessor(Task predecessor, Task successor) {
        this.predecessor = Objects.requireNonNull(predecessor, "Predecessor task must not be null");
        this.successor = Objects.requireNonNull(successor, "Successor task must not be null");
        validateInvariants();
    }

    public UUID getId() {
        return id;
    }

    public Task getPredecessor() {
        return predecessor;
    }

    public Task getSuccessor() {
        return successor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskDependency that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
