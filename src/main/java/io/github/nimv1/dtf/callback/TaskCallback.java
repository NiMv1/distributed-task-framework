package io.github.nimv1.dtf.callback;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskResult;

/**
 * Callback interface for async task completion notification.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public interface TaskCallback {

    /**
     * Called when task completes successfully.
     *
     * @param task the completed task
     * @param result the task result
     */
    void onSuccess(Task task, TaskResult result);

    /**
     * Called when task fails.
     *
     * @param task the failed task
     * @param exception the exception that caused the failure
     */
    void onFailure(Task task, Exception exception);

    /**
     * Called when task times out.
     *
     * @param task the timed out task
     */
    default void onTimeout(Task task) {
        onFailure(task, new RuntimeException("Task timed out: " + task.getId()));
    }

    /**
     * Called when task is cancelled.
     *
     * @param task the cancelled task
     */
    default void onCancelled(Task task) {
        // Default: do nothing
    }
}
