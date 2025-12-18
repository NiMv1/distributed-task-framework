package io.github.nimv1.dtf.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Task.
 * 
 * @author NiMv1
 */
class TaskTest {

    @Test
    void shouldCreateTaskWithDefaults() {
        Task task = Task.builder("test-task").build();
        
        assertNotNull(task.getId());
        assertEquals("test-task", task.getType());
        assertEquals(TaskPriority.NORMAL, task.getPriority());
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertEquals(3, task.getMaxRetries());
        assertEquals(0, task.getRetryCount());
    }

    @Test
    void shouldCreateTaskWithCustomSettings() {
        Task task = Task.builder("email")
                .id("custom-id")
                .payload(Map.of("to", "test@example.com"))
                .priority(TaskPriority.HIGH)
                .maxRetries(5)
                .timeout(60000)
                .build();
        
        assertEquals("custom-id", task.getId());
        assertEquals("email", task.getType());
        assertEquals(TaskPriority.HIGH, task.getPriority());
        assertEquals(5, task.getMaxRetries());
        assertEquals(60000, task.getTimeoutMs());
        assertEquals("test@example.com", task.getPayload().get("to"));
    }

    @Test
    void shouldTransitionToRunning() {
        Task task = Task.builder("test").build();
        
        task.markStarted("worker-1");
        
        assertEquals(TaskStatus.RUNNING, task.getStatus());
        assertEquals("worker-1", task.getWorkerId());
        assertNotNull(task.getStartedAt());
    }

    @Test
    void shouldTransitionToCompleted() {
        Task task = Task.builder("test").build();
        task.markStarted("worker-1");
        
        task.markCompleted();
        
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        assertNotNull(task.getCompletedAt());
    }

    @Test
    void shouldRetryOnFailure() {
        Task task = Task.builder("test").maxRetries(3).build();
        
        task.markFailed("Error 1");
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertEquals(1, task.getRetryCount());
        assertTrue(task.canRetry());
        
        task.markFailed("Error 2");
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertEquals(2, task.getRetryCount());
        assertTrue(task.canRetry());
        
        task.markFailed("Error 3");
        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertEquals(3, task.getRetryCount());
        assertFalse(task.canRetry());
    }

    @Test
    void shouldTransitionToCancelled() {
        Task task = Task.builder("test").build();
        
        task.markCancelled();
        
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
    }

    @Test
    void shouldThrowExceptionForBlankType() {
        assertThrows(IllegalArgumentException.class, () -> 
                Task.builder("").build()
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
                Task.builder("   ").build()
        );
    }
}
