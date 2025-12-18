package io.github.nimv1.dtf.interceptor;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Chain of task interceptors for ordered execution.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TaskInterceptorChain {

    private static final Logger log = LoggerFactory.getLogger(TaskInterceptorChain.class);

    private final List<TaskInterceptor> interceptors = new ArrayList<>();

    public void addInterceptor(TaskInterceptor interceptor) {
        interceptors.add(interceptor);
        interceptors.sort(Comparator.comparingInt(TaskInterceptor::getOrder));
    }

    public void removeInterceptor(TaskInterceptor interceptor) {
        interceptors.remove(interceptor);
    }

    /**
     * Executes preHandle for all interceptors.
     *
     * @return true if all interceptors allow execution
     */
    public boolean applyPreHandle(Task task) {
        for (TaskInterceptor interceptor : interceptors) {
            try {
                if (!interceptor.preHandle(task)) {
                    log.debug("Interceptor {} rejected task {}", interceptor.getClass().getSimpleName(), task.getId());
                    return false;
                }
            } catch (Exception e) {
                log.error("Error in preHandle for task {}: {}", task.getId(), e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * Executes postHandle for all interceptors in reverse order.
     */
    public void applyPostHandle(Task task, TaskResult result) {
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            try {
                interceptors.get(i).postHandle(task, result);
            } catch (Exception e) {
                log.error("Error in postHandle for task {}: {}", task.getId(), e.getMessage());
            }
        }
    }

    /**
     * Executes afterCompletion for all interceptors in reverse order.
     */
    public void applyAfterCompletion(Task task, TaskResult result, Exception exception) {
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            try {
                interceptors.get(i).afterCompletion(task, result, exception);
            } catch (Exception e) {
                log.error("Error in afterCompletion for task {}: {}", task.getId(), e.getMessage());
            }
        }
    }

    public List<TaskInterceptor> getInterceptors() {
        return new ArrayList<>(interceptors);
    }

    public int size() {
        return interceptors.size();
    }
}
