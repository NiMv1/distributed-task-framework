package io.github.nimv1.dtf.event;

/**
 * Listener interface for task lifecycle events.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
@FunctionalInterface
public interface TaskEventListener {

    /**
     * Called when a task event occurs.
     *
     * @param event the task event
     */
    void onEvent(TaskEvent event);
}
