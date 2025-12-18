package io.github.nimv1.dtf.interceptor;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskResult;

/**
 * Interceptor for task execution lifecycle.
 * 
 * <p>Allows custom logic before and after task execution.</p>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public interface TaskInterceptor {

    /**
     * Called before task execution.
     *
     * @param task the task to be executed
     * @return true to continue execution, false to skip
     */
    default boolean preHandle(Task task) {
        return true;
    }

    /**
     * Called after successful task execution.
     *
     * @param task the executed task
     * @param result the execution result
     */
    default void postHandle(Task task, TaskResult result) {
    }

    /**
     * Called after task execution completes (success or failure).
     *
     * @param task the executed task
     * @param result the execution result (may be null on error)
     * @param exception the exception if execution failed (may be null)
     */
    default void afterCompletion(Task task, TaskResult result, Exception exception) {
    }

    /**
     * Returns the order of this interceptor.
     * Lower values have higher priority.
     */
    default int getOrder() {
        return 0;
    }
}
