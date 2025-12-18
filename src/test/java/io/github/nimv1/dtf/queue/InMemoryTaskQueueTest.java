package io.github.nimv1.dtf.queue;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskPriority;
import io.github.nimv1.dtf.core.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InMemoryTaskQueue.
 * 
 * @author NiMv1
 */
class InMemoryTaskQueueTest {

    private InMemoryTaskQueue queue;

    @BeforeEach
    void setUp() {
        queue = new InMemoryTaskQueue();
    }

    @Test
    void shouldSubmitAndPollTask() {
        Task task = Task.builder("test").build();
        
        queue.submit(task);
        assertEquals(1, queue.size());
        
        Optional<Task> polled = queue.poll();
        assertTrue(polled.isPresent());
        assertEquals(task.getId(), polled.get().getId());
        assertEquals(0, queue.size());
    }

    @Test
    void shouldPollByPriority() {
        Task lowPriority = Task.builder("low").priority(TaskPriority.LOW).build();
        Task normalPriority = Task.builder("normal").priority(TaskPriority.NORMAL).build();
        Task highPriority = Task.builder("high").priority(TaskPriority.HIGH).build();
        
        queue.submit(lowPriority);
        queue.submit(normalPriority);
        queue.submit(highPriority);
        
        // Should poll in priority order: HIGH, NORMAL, LOW
        assertEquals("high", queue.poll().get().getType());
        assertEquals("normal", queue.poll().get().getType());
        assertEquals("low", queue.poll().get().getType());
    }

    @Test
    void shouldPollBatch() {
        for (int i = 0; i < 5; i++) {
            queue.submit(Task.builder("task-" + i).build());
        }
        
        List<Task> batch = queue.pollBatch(3);
        assertEquals(3, batch.size());
        assertEquals(2, queue.size());
    }

    @Test
    void shouldGetTaskById() {
        Task task = Task.builder("test").id("my-task-id").build();
        queue.submit(task);
        
        Optional<Task> found = queue.getById("my-task-id");
        assertTrue(found.isPresent());
        assertEquals("my-task-id", found.get().getId());
        
        Optional<Task> notFound = queue.getById("non-existent");
        assertFalse(notFound.isPresent());
    }

    @Test
    void shouldCancelPendingTask() {
        Task task = Task.builder("test").build();
        queue.submit(task);
        
        boolean cancelled = queue.cancel(task.getId());
        assertTrue(cancelled);
        assertEquals(0, queue.size());
        
        // Cannot cancel again
        assertFalse(queue.cancel(task.getId()));
    }

    @Test
    void shouldMoveToDeadLetterQueue() {
        Task task = Task.builder("test").maxRetries(0).build();
        task.markFailed("Test error");
        
        queue.submit(task);
        queue.deadLetter(task);
        
        List<Task> dlq = queue.getDeadLetterQueue();
        assertEquals(1, dlq.size());
        assertEquals(TaskStatus.FAILED, dlq.get(0).getStatus());
    }

    @Test
    void shouldRetryTask() {
        Task task = Task.builder("test").maxRetries(3).build();
        queue.submit(task);
        queue.poll(); // Remove from queue
        
        task.markFailed("Error");
        queue.retry(task);
        
        assertEquals(1, queue.size());
        assertEquals(1, task.getRetryCount());
    }

    @Test
    void shouldReturnEmptyOnEmptyQueue() {
        Optional<Task> polled = queue.poll();
        assertFalse(polled.isPresent());
        
        List<Task> batch = queue.pollBatch(10);
        assertTrue(batch.isEmpty());
    }
}
