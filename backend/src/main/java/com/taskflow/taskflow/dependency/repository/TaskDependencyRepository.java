package com.taskflow.taskflow.dependency.repository;

import com.taskflow.taskflow.dependency.entity.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskDependencyRepository extends JpaRepository<TaskDependency, UUID> {

    List<TaskDependency> findByPredecessorId(UUID predecessorTaskId);

    List<TaskDependency> findBySuccessorId(UUID successorTaskId);

    boolean existsByPredecessorIdAndSuccessorId(UUID predecessorTaskId, UUID successorTaskId);

    Optional<TaskDependency> findByPredecessorIdAndSuccessorId(UUID predecessorTaskId, UUID successorTaskId);

    void deleteByPredecessorIdAndSuccessorId(UUID predecessorTaskId, UUID successorTaskId);

    @Query("SELECT td FROM TaskDependency td WHERE td.predecessor.project.id = :projectId")
    List<TaskDependency> findByProjectId(@Param("projectId") UUID projectId);
}
