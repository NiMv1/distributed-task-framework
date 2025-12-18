package io.github.nimv1.dtf.metrics;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects metrics for task execution.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TaskMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(TaskMetricsCollector.class);

    private final AtomicLong totalSubmitted = new AtomicLong(0);
    private final AtomicLong totalCompleted = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final AtomicLong totalRetried = new AtomicLong(0);
    private final AtomicLong totalCancelled = new AtomicLong(0);

    private final Map<String, AtomicLong> submittedByType = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> completedByType = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> failedByType = new ConcurrentHashMap<>();

    private final Map<String, Long> totalExecutionTimeByType = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> executionCountByType = new ConcurrentHashMap<>();

    /**
     * Records a task submission.
     */
    public void recordSubmission(Task task) {
        totalSubmitted.incrementAndGet();
        submittedByType.computeIfAbsent(task.getType(), k -> new AtomicLong(0)).incrementAndGet();
        log.debug("Recorded submission for task type: {}", task.getType());
    }

    /**
     * Records a task completion.
     */
    public void recordCompletion(Task task, Duration executionTime) {
        totalCompleted.incrementAndGet();
        completedByType.computeIfAbsent(task.getType(), k -> new AtomicLong(0)).incrementAndGet();
        
        totalExecutionTimeByType.merge(task.getType(), executionTime.toMillis(), Long::sum);
        executionCountByType.computeIfAbsent(task.getType(), k -> new AtomicLong(0)).incrementAndGet();
        
        log.debug("Recorded completion for task type: {} ({}ms)", task.getType(), executionTime.toMillis());
    }

    /**
     * Records a task failure.
     */
    public void recordFailure(Task task) {
        totalFailed.incrementAndGet();
        failedByType.computeIfAbsent(task.getType(), k -> new AtomicLong(0)).incrementAndGet();
        log.debug("Recorded failure for task type: {}", task.getType());
    }

    /**
     * Records a task retry.
     */
    public void recordRetry(Task task) {
        totalRetried.incrementAndGet();
        log.debug("Recorded retry for task type: {}", task.getType());
    }

    /**
     * Records a task cancellation.
     */
    public void recordCancellation(Task task) {
        totalCancelled.incrementAndGet();
        log.debug("Recorded cancellation for task type: {}", task.getType());
    }

    // Getters for metrics
    public long getTotalSubmitted() { return totalSubmitted.get(); }
    public long getTotalCompleted() { return totalCompleted.get(); }
    public long getTotalFailed() { return totalFailed.get(); }
    public long getTotalRetried() { return totalRetried.get(); }
    public long getTotalCancelled() { return totalCancelled.get(); }

    public long getSubmittedByType(String type) {
        return submittedByType.getOrDefault(type, new AtomicLong(0)).get();
    }

    public long getCompletedByType(String type) {
        return completedByType.getOrDefault(type, new AtomicLong(0)).get();
    }

    public long getFailedByType(String type) {
        return failedByType.getOrDefault(type, new AtomicLong(0)).get();
    }

    /**
     * Returns the average execution time for a task type in milliseconds.
     */
    public double getAverageExecutionTime(String type) {
        long totalTime = totalExecutionTimeByType.getOrDefault(type, 0L);
        long count = executionCountByType.getOrDefault(type, new AtomicLong(0)).get();
        return count > 0 ? (double) totalTime / count : 0.0;
    }

    /**
     * Returns the success rate as a percentage.
     */
    public double getSuccessRate() {
        long total = totalCompleted.get() + totalFailed.get();
        return total > 0 ? (double) totalCompleted.get() / total * 100 : 100.0;
    }

    /**
     * Returns a snapshot of all metrics.
     */
    public Map<String, Object> getSnapshot() {
        Map<String, Object> snapshot = new ConcurrentHashMap<>();
        snapshot.put("totalSubmitted", totalSubmitted.get());
        snapshot.put("totalCompleted", totalCompleted.get());
        snapshot.put("totalFailed", totalFailed.get());
        snapshot.put("totalRetried", totalRetried.get());
        snapshot.put("totalCancelled", totalCancelled.get());
        snapshot.put("successRate", getSuccessRate());
        snapshot.put("submittedByType", Map.copyOf(submittedByType));
        snapshot.put("completedByType", Map.copyOf(completedByType));
        snapshot.put("failedByType", Map.copyOf(failedByType));
        return snapshot;
    }

    /**
     * Resets all metrics.
     */
    public void reset() {
        totalSubmitted.set(0);
        totalCompleted.set(0);
        totalFailed.set(0);
        totalRetried.set(0);
        totalCancelled.set(0);
        submittedByType.clear();
        completedByType.clear();
        failedByType.clear();
        totalExecutionTimeByType.clear();
        executionCountByType.clear();
        log.info("Metrics reset");
    }

    @Override
    public String toString() {
        return "TaskMetrics{submitted=" + totalSubmitted.get() + 
               ", completed=" + totalCompleted.get() + 
               ", failed=" + totalFailed.get() + 
               ", successRate=" + String.format("%.1f%%", getSuccessRate()) + "}";
    }
}
