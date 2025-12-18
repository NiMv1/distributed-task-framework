package io.github.nimv1.dtf.batch;

import io.github.nimv1.dtf.core.Task;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TaskBatch.
 */
class TaskBatchTest {

    @Test
    void shouldCreateEmptyBatch() {
        TaskBatch batch = new TaskBatch();
        
        assertNotNull(batch.getBatchId());
        assertEquals(0, batch.getTotalCount());
        assertEquals(TaskBatch.BatchStatus.PENDING, batch.getStatus());
    }

    @Test
    void shouldCreateBatchWithTasks() {
        Task task1 = Task.builder("test").payload(Map.of()).build();
        Task task2 = Task.builder("test").payload(Map.of()).build();
        
        TaskBatch batch = new TaskBatch(Arrays.asList(task1, task2));
        
        assertEquals(2, batch.getTotalCount());
        assertEquals(TaskBatch.BatchStatus.PENDING, batch.getStatus());
    }

    @Test
    void shouldTrackProgress() {
        Task task1 = Task.builder("test").payload(Map.of()).build();
        Task task2 = Task.builder("test").payload(Map.of()).build();
        TaskBatch batch = new TaskBatch(Arrays.asList(task1, task2));
        
        batch.start();
        assertEquals(TaskBatch.BatchStatus.RUNNING, batch.getStatus());
        assertEquals(0.0, batch.getProgress());
        
        batch.markTaskCompleted();
        assertEquals(50.0, batch.getProgress());
        
        batch.markTaskCompleted();
        assertEquals(100.0, batch.getProgress());
        assertEquals(TaskBatch.BatchStatus.COMPLETED, batch.getStatus());
    }

    @Test
    void shouldMarkPartiallyCompleted() {
        Task task1 = Task.builder("test").payload(Map.of()).build();
        Task task2 = Task.builder("test").payload(Map.of()).build();
        TaskBatch batch = new TaskBatch(Arrays.asList(task1, task2));
        
        batch.start();
        batch.markTaskCompleted();
        batch.markTaskFailed();
        
        assertEquals(TaskBatch.BatchStatus.PARTIALLY_COMPLETED, batch.getStatus());
        assertEquals(1, batch.getCompletedCount());
        assertEquals(1, batch.getFailedCount());
    }

    @Test
    void shouldAddTasksToBatch() {
        TaskBatch batch = new TaskBatch();
        Task task = Task.builder("test").payload(Map.of()).build();
        
        batch.addTask(task);
        
        assertEquals(1, batch.getTotalCount());
    }

    @Test
    void shouldCalculatePendingCount() {
        Task task1 = Task.builder("test").payload(Map.of()).build();
        Task task2 = Task.builder("test").payload(Map.of()).build();
        Task task3 = Task.builder("test").payload(Map.of()).build();
        TaskBatch batch = new TaskBatch(Arrays.asList(task1, task2, task3));
        
        batch.start();
        assertEquals(3, batch.getPendingCount());
        
        batch.markTaskCompleted();
        assertEquals(2, batch.getPendingCount());
    }
}
