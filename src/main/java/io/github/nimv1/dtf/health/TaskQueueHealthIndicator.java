package io.github.nimv1.dtf.health;

import io.github.nimv1.dtf.queue.TaskQueue;

import java.util.HashMap;
import java.util.Map;

/**
 * Health indicator for task queue monitoring.
 * 
 * <p>Can be integrated with Spring Boot Actuator or other health check systems.</p>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TaskQueueHealthIndicator {

    private final TaskQueue taskQueue;
    private final String queueName;
    private final int maxQueueSize;

    public TaskQueueHealthIndicator(TaskQueue taskQueue, String queueName) {
        this(taskQueue, queueName, 10000);
    }

    public TaskQueueHealthIndicator(TaskQueue taskQueue, String queueName, int maxQueueSize) {
        this.taskQueue = taskQueue;
        this.queueName = queueName;
        this.maxQueueSize = maxQueueSize;
    }

    /**
     * Checks the health of the task queue.
     *
     * @return health status
     */
    public HealthStatus check() {
        try {
            long size = taskQueue.size();
            long deadLetterSize = 0; // Dead letter size not available in interface

            Map<String, Object> details = new HashMap<>();
            details.put("queue", queueName);
            details.put("size", size);
            details.put("deadLetterSize", deadLetterSize);
            details.put("maxSize", maxQueueSize);

            if (size > maxQueueSize) {
                return new HealthStatus(Status.DOWN, "Queue size exceeds maximum", details);
            }

            if (deadLetterSize > 100) {
                return new HealthStatus(Status.DEGRADED, "High dead letter count", details);
            }

            return new HealthStatus(Status.UP, "Queue is healthy", details);
        } catch (Exception e) {
            Map<String, Object> details = new HashMap<>();
            details.put("error", e.getMessage());
            return new HealthStatus(Status.DOWN, "Queue check failed", details);
        }
    }

    public enum Status {
        UP, DOWN, DEGRADED
    }

    public static class HealthStatus {
        private final Status status;
        private final String message;
        private final Map<String, Object> details;

        public HealthStatus(Status status, String message, Map<String, Object> details) {
            this.status = status;
            this.message = message;
            this.details = details;
        }

        public Status getStatus() { return status; }
        public String getMessage() { return message; }
        public Map<String, Object> getDetails() { return details; }

        public boolean isHealthy() {
            return status == Status.UP;
        }

        @Override
        public String toString() {
            return "HealthStatus{status=" + status + ", message='" + message + "'}";
        }
    }
}
