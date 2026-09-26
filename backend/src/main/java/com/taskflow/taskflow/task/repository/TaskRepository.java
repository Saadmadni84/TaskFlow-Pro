package com.taskflow.taskflow.task.repository;

import com.taskflow.taskflow.task.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByProjectId(UUID projectId);

    Optional<Task> findByProjectIdAndId(UUID projectId, UUID id);

    long countByProjectId(UUID projectId);
}
