package com.taskflow.taskflow.dependency;

import com.taskflow.taskflow.common.exception.CycleDetectedException;
import com.taskflow.taskflow.common.exception.SelfDependencyException;
import com.taskflow.taskflow.dependency.dto.CreateDependencyRequest;
import com.taskflow.taskflow.dependency.dto.DependencyResponse;
import com.taskflow.taskflow.dependency.entity.TaskDependency;
import com.taskflow.taskflow.dependency.graph.AffectedSubgraph;
import com.taskflow.taskflow.dependency.repository.TaskDependencyRepository;
import com.taskflow.taskflow.dependency.service.TaskDependencyService;
import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.entity.TaskStatus;
import com.taskflow.taskflow.task.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CycleDetectionIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskDependencyRepository dependencyRepository;

    @Autowired
    private TaskDependencyService dependencyService;

    private Project project;
    private Task taskA;
    private Task taskB;
    private Task taskC;

    @BeforeEach
    void setUp() {
        dependencyRepository.deleteAll();
        taskRepository.deleteAll();
        projectRepository.deleteAll();

        project = projectRepository.saveAndFlush(new Project("DAG Integration Project", "Testing cycle detection in DB"));

        taskA = taskRepository.saveAndFlush(new Task(project, "Task A", "Schema", TaskStatus.DONE, null, null, null));
        taskB = taskRepository.saveAndFlush(new Task(project, "Task B", "API", TaskStatus.IN_PROGRESS, null, null, null));
        taskC = taskRepository.saveAndFlush(new Task(project, "Task C", "Frontend", TaskStatus.BACKLOG, null, null, null));
    }

    @Test
    @DisplayName("Should prevent cycle creation and roll back transactionally without persisting invalid edge")
    void shouldRejectCycleAndMaintainDatabaseIntegrity() {
        // Step 1: Create A -> B
        DependencyResponse dep1 = dependencyService.createDependency(new CreateDependencyRequest(taskA.getId(), taskB.getId()));
        assertThat(dep1).isNotNull();

        // Step 2: Create B -> C
        DependencyResponse dep2 = dependencyService.createDependency(new CreateDependencyRequest(taskB.getId(), taskC.getId()));
        assertThat(dep2).isNotNull();

        assertThat(dependencyRepository.count()).isEqualTo(2);

        // Step 3: Attempt to create C -> A (closes A -> B -> C -> A cycle)
        assertThatThrownBy(() -> dependencyService.createDependency(new CreateDependencyRequest(taskC.getId(), taskA.getId())))
                .isInstanceOf(CycleDetectedException.class)
                .hasMessageContaining("Circular dependency detected");

        // Verify the database contains ONLY the 2 valid dependencies; C -> A was NEVER persisted
        List<TaskDependency> remainingDeps = dependencyRepository.findAll();
        assertThat(remainingDeps).hasSize(2);
        assertThat(dependencyRepository.existsByPredecessorIdAndSuccessorId(taskC.getId(), taskA.getId())).isFalse();
    }

    @Test
    @DisplayName("Should reject self-dependency at service level before persistence")
    void shouldRejectSelfDependency() {
        assertThatThrownBy(() -> dependencyService.createDependency(new CreateDependencyRequest(taskA.getId(), taskA.getId())))
                .isInstanceOf(SelfDependencyException.class)
                .hasMessageContaining("Self-dependency is forbidden");

        assertThat(dependencyRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should compute affected subgraph and topological ordering from persisted graph")
    void shouldComputeAffectedSubgraphFromDatabase() {
        dependencyService.createDependency(new CreateDependencyRequest(taskA.getId(), taskB.getId()));
        dependencyService.createDependency(new CreateDependencyRequest(taskB.getId(), taskC.getId()));

        AffectedSubgraph subgraph = dependencyService.getAffectedSubgraph(taskA.getId());

        assertThat(subgraph.rootTaskId()).isEqualTo(taskA.getId());
        assertThat(subgraph.affectedTaskIds()).containsExactlyInAnyOrder(taskB.getId(), taskC.getId());
        assertThat(subgraph.topologicalOrder()).containsExactly(taskB.getId(), taskC.getId());
    }
}
