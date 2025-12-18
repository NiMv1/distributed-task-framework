package io.github.nimv1.dtf.dlq;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DeadLetterQueueHandler.
 */
class DeadLetterQueueHandlerTest {

    private DeadLetterQueueHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DeadLetterQueueHandler();
    }

    private Task createTask(String type) {
        return Task.builder(type).build();
    }

    @Test
    void shouldAddTaskToDeadLetter() {
        Task task = createTask("test-task");
        
        handler.addToDeadLetter(task, "Max retries exceeded", new RuntimeException("Test error"));
        
        assertEquals(1, handler.size());
        assertTrue(handler.get(task.getId()).isPresent());
    }

    @Test
    void shouldGetTaskFromDeadLetter() {
        Task task = createTask("test-task");
        handler.addToDeadLetter(task, "Test reason", null);
        
        Optional<DeadLetterQueueHandler.DeadLetterEntry> entry = handler.get(task.getId());
        
        assertTrue(entry.isPresent());
        assertEquals(task.getId(), entry.get().getTask().getId());
        assertEquals("Test reason", entry.get().getReason());
    }

    @Test
    void shouldRemoveTaskFromDeadLetter() {
        Task task = createTask("test-task");
        handler.addToDeadLetter(task, "Test reason", null);
        
        boolean removed = handler.remove(task.getId());
        
        assertTrue(removed);
        assertEquals(0, handler.size());
    }

    @Test
    void shouldReturnFalseWhenRemovingNonExistentTask() {
        boolean removed = handler.remove("non-existent-id");
        
        assertFalse(removed);
    }

    @Test
    void shouldGetAllEntries() {
        Task task1 = createTask("task-1");
        Task task2 = createTask("task-2");
        handler.addToDeadLetter(task1, "Reason 1", null);
        handler.addToDeadLetter(task2, "Reason 2", null);
        
        List<DeadLetterQueueHandler.DeadLetterEntry> entries = handler.getAll();
        
        assertEquals(2, entries.size());
    }

    @Test
    void shouldClearAllEntries() {
        Task task1 = createTask("task-1");
        Task task2 = createTask("task-2");
        handler.addToDeadLetter(task1, "Reason 1", null);
        handler.addToDeadLetter(task2, "Reason 2", null);
        
        handler.clear();
        
        assertEquals(0, handler.size());
    }

    @Test
    void shouldRetryTask() {
        Task task = createTask("test-task");
        task.markFailed("Original error");
        handler.addToDeadLetter(task, "Max retries", null);
        
        Optional<Task> retried = handler.retry(task.getId());
        
        assertTrue(retried.isPresent());
        assertEquals(TaskStatus.PENDING, retried.get().getStatus());
        assertEquals(0, handler.size());
    }

    @Test
    void shouldReturnEmptyWhenRetryingNonExistentTask() {
        Optional<Task> retried = handler.retry("non-existent-id");
        
        assertFalse(retried.isPresent());
    }

    @Test
    void shouldRetryAllTasks() {
        Task task1 = createTask("task-1");
        Task task2 = createTask("task-2");
        handler.addToDeadLetter(task1, "Reason 1", null);
        handler.addToDeadLetter(task2, "Reason 2", null);
        
        List<Task> retried = handler.retryAll();
        
        assertEquals(2, retried.size());
        assertEquals(0, handler.size());
    }

    @Test
    void shouldNotifyListeners() {
        AtomicInteger notificationCount = new AtomicInteger(0);
        handler.addListener(entry -> notificationCount.incrementAndGet());
        
        Task task = createTask("test-task");
        handler.addToDeadLetter(task, "Test reason", null);
        
        assertEquals(1, notificationCount.get());
    }

    @Test
    void shouldRemoveListener() {
        AtomicInteger notificationCount = new AtomicInteger(0);
        java.util.function.Consumer<DeadLetterQueueHandler.DeadLetterEntry> listener = 
            entry -> notificationCount.incrementAndGet();
        
        handler.addListener(listener);
        handler.removeListener(listener);
        
        Task task = createTask("test-task");
        handler.addToDeadLetter(task, "Test reason", null);
        
        assertEquals(0, notificationCount.get());
    }

    @Test
    void shouldHandleListenerException() {
        handler.addListener(entry -> { throw new RuntimeException("Listener error"); });
        
        Task task = createTask("test-task");
        // Should not throw
        assertDoesNotThrow(() -> handler.addToDeadLetter(task, "Test reason", null));
        assertEquals(1, handler.size());
    }
}
