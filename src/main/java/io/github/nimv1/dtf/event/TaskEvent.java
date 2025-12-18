package io.github.nimv1.dtf.event;

import io.github.nimv1.dtf.core.Task;

import java.time.Instant;

/**
 * Base class for task lifecycle events.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public abstract class TaskEvent {

    private final Task task;
    private final Instant timestamp;

    protected TaskEvent(Task task) {
        this.task = task;
        this.timestamp = Instant.now();
    }

    public Task getTask() { return task; }
    public String getTaskId() { return task.getId(); }
    public String getTaskType() { return task.getType(); }
    public Instant getTimestamp() { return timestamp; }

    public abstract EventType getEventType();

    public enum EventType {
        SUBMITTED, STARTED, COMPLETED, FAILED, RETRIED, CANCELLED, DEAD_LETTERED
    }
}
