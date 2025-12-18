package io.github.nimv1.dtf.priority;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PriorityTaskQueue.
 */
class PriorityTaskQueueTest {

    private PriorityTaskQueue queue;

    @BeforeEach
    void setUp() {
        queue = new PriorityTaskQueue();
    }

    @Test
    void shouldSubmitAndPollTask() {
        Task task = Task.builder("test").payload(Map.of()).build();
        
        queue.submit(task);
        
        assertEquals(1, queue.size());
        Optional<Task> polled = queue.poll();
        assertTrue(polled.isPresent());
        assertEquals(task.getId(), polled.get().getId());
    }

    @Test
    void shouldPollByPriority() {
        Task lowTask = Task.builder("test").priority(TaskPriority.LOW).payload(Map.of()).build();
        Task highTask = Task.builder("test").priority(TaskPriority.HIGH).payload(Map.of()).build();
        Task criticalTask = Task.builder("test").priority(TaskPriority.CRITICAL).payload(Map.of()).build();
        
        queue.submit(lowTask);
        queue.submit(highTask);
        queue.submit(criticalTask);
        
        // Critical should come first (lowest ordinal)
        Optional<Task> first = queue.poll();
        assertTrue(first.isPresent());
        assertEquals(TaskPriority.CRITICAL, first.get().getPriority());
    }

    @Test
    void shouldCountByPriority() {
        queue.submit(Task.builder("test").priority(TaskPriority.LOW).payload(Map.of()).build());
        queue.submit(Task.builder("test").priority(TaskPriority.LOW).payload(Map.of()).build());
        queue.submit(Task.builder("test").priority(TaskPriority.HIGH).payload(Map.of()).build());
        
        Map<TaskPriority, Long> counts = queue.countByPriority();
        
        assertEquals(2L, counts.get(TaskPriority.LOW));
        assertEquals(1L, counts.get(TaskPriority.HIGH));
        assertEquals(0L, counts.get(TaskPriority.NORMAL));
    }

    @Test
    void shouldMoveToDeadLetter() {
        Task task = Task.builder("test").payload(Map.of()).build();
        queue.submit(task);
        queue.poll(); // Remove from queue first
        
        queue.deadLetter(task);
        
        assertEquals(0, queue.size());
        assertEquals(1, queue.deadLetterSize());
    }

    @Test
    void shouldGetById() {
        Task task = Task.builder("test").payload(Map.of()).build();
        queue.submit(task);
        
        Optional<Task> found = queue.getById(task.getId());
        
        assertTrue(found.isPresent());
        assertEquals(task.getId(), found.get().getId());
    }

    @Test
    void shouldRetryTask() {
        Task task = Task.builder("test").payload(Map.of()).build();
        queue.submit(task);
        queue.poll();
        
        queue.retry(task);
        
        assertEquals(1, queue.size());
        // RetryCount is managed by Task, not by queue
    }
}
