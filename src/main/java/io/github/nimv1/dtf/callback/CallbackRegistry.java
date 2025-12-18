package io.github.nimv1.dtf.callback;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for task callbacks.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class CallbackRegistry {

    private static final Logger log = LoggerFactory.getLogger(CallbackRegistry.class);

    private final Map<String, TaskCallback> callbacks = new ConcurrentHashMap<>();

    /**
     * Registers a callback for a task.
     */
    public void register(String taskId, TaskCallback callback) {
        callbacks.put(taskId, callback);
        log.debug("Callback registered for task {}", taskId);
    }

    /**
     * Unregisters a callback.
     */
    public void unregister(String taskId) {
        callbacks.remove(taskId);
    }

    /**
     * Notifies success callback.
     */
    public void notifySuccess(Task task, TaskResult result) {
        TaskCallback callback = callbacks.remove(task.getId());
        if (callback != null) {
            try {
                callback.onSuccess(task, result);
            } catch (Exception e) {
                log.error("Error in success callback for task {}: {}", task.getId(), e.getMessage());
            }
        }
    }

    /**
     * Notifies failure callback.
     */
    public void notifyFailure(Task task, Exception exception) {
        TaskCallback callback = callbacks.remove(task.getId());
        if (callback != null) {
            try {
                callback.onFailure(task, exception);
            } catch (Exception e) {
                log.error("Error in failure callback for task {}: {}", task.getId(), e.getMessage());
            }
        }
    }

    /**
     * Notifies timeout callback.
     */
    public void notifyTimeout(Task task) {
        TaskCallback callback = callbacks.remove(task.getId());
        if (callback != null) {
            try {
                callback.onTimeout(task);
            } catch (Exception e) {
                log.error("Error in timeout callback for task {}: {}", task.getId(), e.getMessage());
            }
        }
    }

    /**
     * Notifies cancelled callback.
     */
    public void notifyCancelled(Task task) {
        TaskCallback callback = callbacks.remove(task.getId());
        if (callback != null) {
            try {
                callback.onCancelled(task);
            } catch (Exception e) {
                log.error("Error in cancelled callback for task {}: {}", task.getId(), e.getMessage());
            }
        }
    }

    /**
     * Returns the number of registered callbacks.
     */
    public int size() {
        return callbacks.size();
    }
}
