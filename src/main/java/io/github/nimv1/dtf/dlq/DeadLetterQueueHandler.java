package io.github.nimv1.dtf.dlq;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Handles tasks that have failed and been moved to the dead letter queue.
 * Provides retry, inspection, and cleanup capabilities.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class DeadLetterQueueHandler {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueueHandler.class);

    private final Map<String, DeadLetterEntry> deadLetterQueue = new ConcurrentHashMap<>();
    private final List<Consumer<DeadLetterEntry>> listeners = new ArrayList<>();

    /**
     * Adds a task to the dead letter queue.
     */
    public void addToDeadLetter(Task task, String reason, Throwable cause) {
        DeadLetterEntry entry = new DeadLetterEntry(task, reason, cause, Instant.now());
        deadLetterQueue.put(task.getId(), entry);
        log.warn("Task {} added to DLQ: {}", task.getId(), reason);
        notifyListeners(entry);
    }

    /**
     * Gets a task from the dead letter queue.
     */
    public Optional<DeadLetterEntry> get(String taskId) {
        return Optional.ofNullable(deadLetterQueue.get(taskId));
    }

    /**
     * Removes a task from the dead letter queue.
     */
    public boolean remove(String taskId) {
        DeadLetterEntry removed = deadLetterQueue.remove(taskId);
        if (removed != null) {
            log.info("Task {} removed from DLQ", taskId);
            return true;
        }
        return false;
    }

    /**
     * Returns all entries in the dead letter queue.
     */
    public List<DeadLetterEntry> getAll() {
        return new ArrayList<>(deadLetterQueue.values());
    }

    /**
     * Returns the number of entries in the dead letter queue.
     */
    public int size() {
        return deadLetterQueue.size();
    }

    /**
     * Clears all entries from the dead letter queue.
     */
    public void clear() {
        int count = deadLetterQueue.size();
        deadLetterQueue.clear();
        log.info("DLQ cleared, {} entries removed", count);
    }

    /**
     * Retries a task from the dead letter queue.
     * Returns the task for reprocessing if found.
     */
    public Optional<Task> retry(String taskId) {
        DeadLetterEntry entry = deadLetterQueue.remove(taskId);
        if (entry != null) {
            Task task = entry.getTask();
            task.resetForRetry();
            log.info("Task {} removed from DLQ for retry", taskId);
            return Optional.of(task);
        }
        return Optional.empty();
    }

    /**
     * Retries all tasks in the dead letter queue.
     * Returns the list of tasks for reprocessing.
     */
    public List<Task> retryAll() {
        List<Task> tasks = new ArrayList<>();
        for (String taskId : new ArrayList<>(deadLetterQueue.keySet())) {
            retry(taskId).ifPresent(tasks::add);
        }
        log.info("Retrying {} tasks from DLQ", tasks.size());
        return tasks;
    }

    /**
     * Adds a listener for dead letter events.
     */
    public void addListener(Consumer<DeadLetterEntry> listener) {
        listeners.add(listener);
    }

    /**
     * Removes a listener.
     */
    public void removeListener(Consumer<DeadLetterEntry> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(DeadLetterEntry entry) {
        for (Consumer<DeadLetterEntry> listener : listeners) {
            try {
                listener.accept(entry);
            } catch (Exception e) {
                log.error("Error notifying DLQ listener", e);
            }
        }
    }

    /**
     * Represents an entry in the dead letter queue.
     */
    public static class DeadLetterEntry {
        private final Task task;
        private final String reason;
        private final Throwable cause;
        private final Instant addedAt;

        public DeadLetterEntry(Task task, String reason, Throwable cause, Instant addedAt) {
            this.task = task;
            this.reason = reason;
            this.cause = cause;
            this.addedAt = addedAt;
        }

        public Task getTask() { return task; }
        public String getReason() { return reason; }
        public Throwable getCause() { return cause; }
        public Instant getAddedAt() { return addedAt; }

        @Override
        public String toString() {
            return "DeadLetterEntry{taskId=" + task.getId() + 
                   ", type=" + task.getType() + 
                   ", reason=" + reason + 
                   ", addedAt=" + addedAt + "}";
        }
    }
}
