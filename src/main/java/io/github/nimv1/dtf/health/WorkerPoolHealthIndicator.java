package io.github.nimv1.dtf.health;

import io.github.nimv1.dtf.worker.WorkerPool;

import java.util.HashMap;
import java.util.Map;

/**
 * Health indicator for worker pool monitoring.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class WorkerPoolHealthIndicator {

    private final WorkerPool workerPool;

    public WorkerPoolHealthIndicator(WorkerPool workerPool) {
        this.workerPool = workerPool;
    }

    /**
     * Checks the health of the worker pool.
     *
     * @return health status
     */
    public TaskQueueHealthIndicator.HealthStatus check() {
        Map<String, Object> details = new HashMap<>();
        details.put("name", workerPool.getName());
        details.put("workerCount", workerPool.getWorkerCount());
        details.put("running", workerPool.isRunning());
        details.put("processedCount", workerPool.getProcessedCount());
        details.put("failedCount", workerPool.getFailedCount());

        if (!workerPool.isRunning()) {
            return new TaskQueueHealthIndicator.HealthStatus(
                    TaskQueueHealthIndicator.Status.DOWN,
                    "Worker pool is not running",
                    details
            );
        }

        double failureRate = calculateFailureRate();
        details.put("failureRate", String.format("%.2f%%", failureRate));

        if (failureRate > 50) {
            return new TaskQueueHealthIndicator.HealthStatus(
                    TaskQueueHealthIndicator.Status.DEGRADED,
                    "High failure rate",
                    details
            );
        }

        return new TaskQueueHealthIndicator.HealthStatus(
                TaskQueueHealthIndicator.Status.UP,
                "Worker pool is healthy",
                details
        );
    }

    private double calculateFailureRate() {
        long total = workerPool.getProcessedCount() + workerPool.getFailedCount();
        if (total == 0) return 0.0;
        return (double) workerPool.getFailedCount() / total * 100;
    }
}
