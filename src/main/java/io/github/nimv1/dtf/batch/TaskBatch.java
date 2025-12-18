package io.github.nimv1.dtf.batch;

import io.github.nimv1.dtf.core.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Represents a batch of tasks for bulk processing.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TaskBatch {

    private final String batchId;
    private final List<Task> tasks;
    private BatchStatus status;
    private int completedCount;
    private int failedCount;

    public TaskBatch() {
        this.batchId = UUID.randomUUID().toString();
        this.tasks = new ArrayList<>();
        this.status = BatchStatus.PENDING;
        this.completedCount = 0;
        this.failedCount = 0;
    }

    public TaskBatch(List<Task> tasks) {
        this();
        this.tasks.addAll(tasks);
    }

    public String getBatchId() { return batchId; }
    public List<Task> getTasks() { return Collections.unmodifiableList(tasks); }
    public BatchStatus getStatus() { return status; }
    public int getCompletedCount() { return completedCount; }
    public int getFailedCount() { return failedCount; }
    public int getTotalCount() { return tasks.size(); }
    public int getPendingCount() { return tasks.size() - completedCount - failedCount; }

    public void addTask(Task task) {
        tasks.add(task);
    }

    public void markTaskCompleted() {
        completedCount++;
        updateStatus();
    }

    public void markTaskFailed() {
        failedCount++;
        updateStatus();
    }

    public void start() {
        this.status = BatchStatus.RUNNING;
    }

    private void updateStatus() {
        if (completedCount + failedCount >= tasks.size()) {
            this.status = failedCount > 0 ? BatchStatus.PARTIALLY_COMPLETED : BatchStatus.COMPLETED;
        }
    }

    public double getProgress() {
        if (tasks.isEmpty()) return 0.0;
        return (double) (completedCount + failedCount) / tasks.size() * 100;
    }

    public boolean isComplete() {
        return status == BatchStatus.COMPLETED || status == BatchStatus.PARTIALLY_COMPLETED;
    }

    public enum BatchStatus {
        PENDING, RUNNING, COMPLETED, PARTIALLY_COMPLETED, FAILED
    }

    @Override
    public String toString() {
        return "TaskBatch{id='" + batchId + "', status=" + status + 
               ", progress=" + String.format("%.1f", getProgress()) + "%" +
               ", completed=" + completedCount + "/" + tasks.size() + "}";
    }
}
