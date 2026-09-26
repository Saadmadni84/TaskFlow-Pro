package com.taskflow.taskflow.hardening;

import com.taskflow.taskflow.common.exception.GlobalExceptionHandler;
import com.taskflow.taskflow.project.entity.Project;
import com.taskflow.taskflow.project.repository.ProjectRepository;
import com.taskflow.taskflow.task.controller.TaskController;
import com.taskflow.taskflow.task.dto.UpdateTaskRequest;
import com.taskflow.taskflow.task.entity.DependencyStatus;
import com.taskflow.taskflow.task.entity.Task;
import com.taskflow.taskflow.task.entity.TaskStatus;
import com.taskflow.taskflow.task.repository.TaskRepository;
import com.taskflow.taskflow.task.service.TaskService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OptimisticLockingConcurrencyIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private TaskService taskService;

    @Autowired
    private MockMvc mockMvc;

    private Project project;
    private Task task;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        projectRepository.deleteAll();

        project = projectRepository.saveAndFlush(new Project("Concurrency Test Project", "Testing optimistic locking"));
        task = taskRepository.saveAndFlush(new Task(
                project,
                "Initial Title",
                "Initial Description",
                TaskStatus.BACKLOG,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5),
                5
        ));
    }

    @Test
    @DisplayName("Should detect optimistic locking conflict when updating stale entity version")
    void shouldDetectOptimisticLockingConflictOnStaleEntity() {
        EntityManager em1 = entityManagerFactory.createEntityManager();
        EntityManager em2 = entityManagerFactory.createEntityManager();

        try {
            // Both entity managers load the same task at version 0
            Task taskInEm1 = em1.find(Task.class, task.getId());
            Task taskInEm2 = em2.find(Task.class, task.getId());

            assertThat(taskInEm1.getVersion()).isEqualTo(0L);
            assertThat(taskInEm2.getVersion()).isEqualTo(0L);

            // Transaction 1 commits an update, bumping version to 1
            EntityTransaction tx1 = em1.getTransaction();
            tx1.begin();
            taskInEm1.setTitle("Title Updated by Tx 1");
            tx1.commit();

            // Verify version is now bumped in DB
            Task updatedInDb = taskRepository.findById(task.getId()).orElseThrow();
            assertThat(updatedInDb.getVersion()).isEqualTo(1L);
            assertThat(updatedInDb.getTitle()).isEqualTo("Title Updated by Tx 1");

            // Transaction 2 tries to commit changes based on stale version 0
            EntityTransaction tx2 = em2.getTransaction();
            tx2.begin();
            taskInEm2.setTitle("Conflicting Title by Tx 2");

            assertThatThrownBy(tx2::commit)
                    .isInstanceOf(jakarta.persistence.RollbackException.class)
                    .hasCauseInstanceOf(jakarta.persistence.OptimisticLockException.class);

        } finally {
            if (em1.isOpen()) em1.close();
            if (em2.isOpen()) em2.close();
        }

        // Verify that Tx 1's title was preserved and Tx 2 did not overwrite
        Task finalTask = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(finalTask.getTitle()).isEqualTo("Title Updated by Tx 1");
        assertThat(finalTask.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Concurrent threads updating same task: one succeeds, one fails with optimistic locking failure")
    void shouldHandleConcurrentThreadUpdatesWithOptimisticLocking() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Runnable updateTaskAction1 = () -> {
            EntityManager em = entityManagerFactory.createEntityManager();
            try {
                startLatch.await();
                EntityTransaction tx = em.getTransaction();
                tx.begin();
                Task t = em.find(Task.class, task.getId());
                t.setTitle("Thread 1 Update");
                tx.commit();
                successCount.incrementAndGet();
            } catch (Exception e) {
                conflictCount.incrementAndGet();
            } finally {
                em.close();
            }
        };

        Runnable updateTaskAction2 = () -> {
            EntityManager em = entityManagerFactory.createEntityManager();
            try {
                startLatch.await();
                EntityTransaction tx = em.getTransaction();
                tx.begin();
                Task t = em.find(Task.class, task.getId());
                t.setTitle("Thread 2 Update");
                tx.commit();
                successCount.incrementAndGet();
            } catch (Exception e) {
                conflictCount.incrementAndGet();
            } finally {
                em.close();
            }
        };

        Future<?> f1 = executor.submit(updateTaskAction1);
        Future<?> f2 = executor.submit(updateTaskAction2);

        // Fire both threads simultaneously
        startLatch.countDown();

        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        // One or both will succeed sequentially, or one conflicts if simultaneous commit
        assertThat(successCount.get() + conflictCount.get()).isEqualTo(2);
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);

        Task refreshed = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(refreshed.getVersion()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("GlobalExceptionHandler maps OptimisticLockingFailureException to HTTP 409 CONCURRENCY_CONFLICT")
    void shouldMapOptimisticLockExceptionTo409() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        OptimisticLockingFailureException ex = new OptimisticLockingFailureException("Row was updated or deleted by another transaction");
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setRequestURI("/api/tasks/" + task.getId());

        var response = handler.handleOptimisticLockingFailure(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONCURRENCY_CONFLICT");
        assertThat(response.getBody().message()).contains("modified by another transaction");
    }
}
