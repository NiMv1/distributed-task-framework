package io.github.nimv1.dtf.interceptor;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interceptor that logs task execution lifecycle.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class LoggingInterceptor implements TaskInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public boolean preHandle(Task task) {
        log.info("Starting task: {} [type={}, priority={}]", task.getId(), task.getType(), task.getPriority());
        return true;
    }

    @Override
    public void postHandle(Task task, TaskResult result) {
        if (result.isSuccess()) {
            log.info("Task {} completed successfully", task.getId());
        } else {
            log.warn("Task {} failed: {}", task.getId(), result.getErrorMessage());
        }
    }

    @Override
    public void afterCompletion(Task task, TaskResult result, Exception exception) {
        if (exception != null) {
            log.error("Task {} threw exception: {}", task.getId(), exception.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE; // First to execute
    }
}
