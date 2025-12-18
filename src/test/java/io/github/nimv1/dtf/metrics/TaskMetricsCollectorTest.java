package io.github.nimv1.dtf.metrics;

import io.github.nimv1.dtf.core.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TaskMetricsCollector.
 */
class TaskMetricsCollectorTest {

    private TaskMetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new TaskMetricsCollector();
    }

    private Task createTask(String type) {
        return Task.builder(type).build();
    }

    @Test
    void shouldRecordSubmission() {
        Task task = createTask("test-task");
        
        collector.recordSubmission(task);
        
        assertEquals(1, collector.getTotalSubmitted());
        assertEquals(1, collector.getSubmittedByType("test-task"));
    }

    @Test
    void shouldRecordCompletion() {
        Task task = createTask("test-task");
        Duration executionTime = Duration.ofMillis(100);
        
        collector.recordCompletion(task, executionTime);
        
        assertEquals(1, collector.getTotalCompleted());
        assertEquals(1, collector.getCompletedByType("test-task"));
        assertEquals(100.0, collector.getAverageExecutionTime("test-task"));
    }

    @Test
    void shouldRecordFailure() {
        Task task = createTask("test-task");
        
        collector.recordFailure(task);
        
        assertEquals(1, collector.getTotalFailed());
        assertEquals(1, collector.getFailedByType("test-task"));
    }

    @Test
    void shouldRecordRetry() {
        Task task = createTask("test-task");
        
        collector.recordRetry(task);
        
        assertEquals(1, collector.getTotalRetried());
    }

    @Test
    void shouldRecordCancellation() {
        Task task = createTask("test-task");
        
        collector.recordCancellation(task);
        
        assertEquals(1, collector.getTotalCancelled());
    }

    @Test
    void shouldCalculateSuccessRate() {
        Task task = createTask("test-task");
        
        // 3 completions, 1 failure = 75% success rate
        collector.recordCompletion(task, Duration.ofMillis(100));
        collector.recordCompletion(task, Duration.ofMillis(100));
        collector.recordCompletion(task, Duration.ofMillis(100));
        collector.recordFailure(task);
        
        assertEquals(75.0, collector.getSuccessRate());
    }

    @Test
    void shouldCalculateAverageExecutionTime() {
        Task task = createTask("test-task");
        
        collector.recordCompletion(task, Duration.ofMillis(100));
        collector.recordCompletion(task, Duration.ofMillis(200));
        collector.recordCompletion(task, Duration.ofMillis(300));
        
        assertEquals(200.0, collector.getAverageExecutionTime("test-task"));
    }

    @Test
    void shouldReturnZeroAverageForUnknownType() {
        assertEquals(0.0, collector.getAverageExecutionTime("unknown"));
    }

    @Test
    void shouldReturn100PercentSuccessRateWhenNoTasks() {
        assertEquals(100.0, collector.getSuccessRate());
    }

    @Test
    void shouldGetSnapshot() {
        Task task = createTask("test-task");
        collector.recordSubmission(task);
        collector.recordCompletion(task, Duration.ofMillis(100));
        
        Map<String, Object> snapshot = collector.getSnapshot();
        
        assertNotNull(snapshot);
        assertEquals(1L, snapshot.get("totalSubmitted"));
        assertEquals(1L, snapshot.get("totalCompleted"));
        assertEquals(0L, snapshot.get("totalFailed"));
    }

    @Test
    void shouldReset() {
        Task task = createTask("test-task");
        collector.recordSubmission(task);
        collector.recordCompletion(task, Duration.ofMillis(100));
        collector.recordFailure(task);
        
        collector.reset();
        
        assertEquals(0, collector.getTotalSubmitted());
        assertEquals(0, collector.getTotalCompleted());
        assertEquals(0, collector.getTotalFailed());
    }

    @Test
    void shouldTrackMultipleTaskTypes() {
        Task task1 = createTask("type-a");
        Task task2 = createTask("type-b");
        
        collector.recordSubmission(task1);
        collector.recordSubmission(task1);
        collector.recordSubmission(task2);
        
        assertEquals(3, collector.getTotalSubmitted());
        assertEquals(2, collector.getSubmittedByType("type-a"));
        assertEquals(1, collector.getSubmittedByType("type-b"));
    }
}
