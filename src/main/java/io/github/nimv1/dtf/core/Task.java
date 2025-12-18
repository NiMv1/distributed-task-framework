package io.github.nimv1.dtf.core;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a distributed task that can be executed by workers.
 * 
 * <p>Tasks are the fundamental unit of work in the framework. Each task has:</p>
 * <ul>
 *   <li>A unique identifier</li>
 *   <li>A type that determines which handler processes it</li>
 *   <li>A payload containing the data to process</li>
 *   <li>Priority and retry configuration</li>
 * </ul>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class Task implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String type;
    private final Map<String, Object> payload;
    private final TaskPriority priority;
    private final int maxRetries;
    private final long timeoutMs;
    
    private TaskStatus status;
    private int retryCount;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private String workerId;
    private String errorMessage;

    private Task(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.type = builder.type;
        this.payload = builder.payload;
        this.priority = builder.priority;
        this.maxRetries = builder.maxRetries;
        this.timeoutMs = builder.timeoutMs;
        this.status = TaskStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = Instant.now();
    }

    public static Builder builder(String type) {
        return new Builder(type);
    }

    // Getters
    public String getId() { return id; }
    public String getType() { return type; }
    public Map<String, Object> getPayload() { return payload; }
    public TaskPriority getPriority() { return priority; }
    public int getMaxRetries() { return maxRetries; }
    public long getTimeoutMs() { return timeoutMs; }
    public TaskStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getWorkerId() { return workerId; }
    public String getErrorMessage() { return errorMessage; }

    // State transitions
    public void markStarted(String workerId) {
        this.status = TaskStatus.RUNNING;
        this.startedAt = Instant.now();
        this.workerId = workerId;
    }

    public void markCompleted() {
        this.status = TaskStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.retryCount++;
        this.errorMessage = errorMessage;
        
        if (retryCount >= maxRetries) {
            this.status = TaskStatus.FAILED;
        } else {
            this.status = TaskStatus.PENDING;
        }
        this.completedAt = Instant.now();
    }

    public void markCancelled() {
        this.status = TaskStatus.CANCELLED;
        this.completedAt = Instant.now();
    }

    public boolean canRetry() {
        return retryCount < maxRetries;
    }

    @Override
    public String toString() {
        return "Task{id='" + id + "', type='" + type + "', status=" + status + 
               ", priority=" + priority + ", retryCount=" + retryCount + "}";
    }

    /**
     * Builder for creating Task instances.
     */
    public static class Builder {
        private String id;
        private final String type;
        private Map<String, Object> payload = Map.of();
        private TaskPriority priority = TaskPriority.NORMAL;
        private int maxRetries = 3;
        private long timeoutMs = 300000; // 5 minutes default

        private Builder(String type) {
            this.type = type;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

        public Builder priority(TaskPriority priority) {
            this.priority = priority;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder timeout(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Task build() {
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("Task type is required");
            }
            return new Task(this);
        }
    }
}
