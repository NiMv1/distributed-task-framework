package io.github.nimv1.dtf.queue;

import io.github.nimv1.dtf.core.Task;

import java.util.List;
import java.util.Optional;

/**
 * Interface for task queue implementations.
 * 
 * <p>Provides abstraction over different queue backends (Redis, Kafka, In-Memory).</p>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public interface TaskQueue {

    /**
     * Submits a task to the queue.
     * 
     * @param task the task to submit
     */
    void submit(Task task);

    /**
     * Polls the next task from the queue.
     * 
     * @return optional containing the next task, or empty if queue is empty
     */
    Optional<Task> poll();

    /**
     * Polls multiple tasks from the queue.
     * 
     * @param maxTasks maximum number of tasks to poll
     * @return list of tasks (may be empty)
     */
    List<Task> pollBatch(int maxTasks);

    /**
     * Returns the current queue size.
     * 
     * @return number of pending tasks
     */
    long size();

    /**
     * Acknowledges successful task completion.
     * 
     * @param task the completed task
     */
    void acknowledge(Task task);

    /**
     * Returns a task to the queue for retry.
     * 
     * @param task the task to retry
     */
    void retry(Task task);

    /**
     * Moves a task to the dead letter queue.
     * 
     * @param task the failed task
     */
    void deadLetter(Task task);

    /**
     * Gets a task by its ID.
     * 
     * @param taskId the task ID
     * @return optional containing the task, or empty if not found
     */
    Optional<Task> getById(String taskId);

    /**
     * Cancels a pending task.
     * 
     * @param taskId the task ID to cancel
     * @return true if task was cancelled, false if not found or already processing
     */
    boolean cancel(String taskId);
}
