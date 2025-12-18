package io.github.nimv1.dtf.event;

import io.github.nimv1.dtf.core.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Publisher for task lifecycle events.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TaskEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TaskEventPublisher.class);

    private final List<TaskEventListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(TaskEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TaskEventListener listener) {
        listeners.remove(listener);
    }

    public void publishSubmitted(Task task) {
        publish(new TaskSubmittedEvent(task));
    }

    public void publishStarted(Task task) {
        publish(new TaskStartedEvent(task));
    }

    public void publishCompleted(Task task) {
        publish(new TaskCompletedEvent(task));
    }

    public void publishFailed(Task task, String error) {
        publish(new TaskFailedEvent(task, error));
    }

    public void publishRetried(Task task, int attempt) {
        publish(new TaskRetriedEvent(task, attempt));
    }

    public void publishCancelled(Task task) {
        publish(new TaskCancelledEvent(task));
    }

    private void publish(TaskEvent event) {
        log.debug("Publishing event: {} for task {}", event.getEventType(), event.getTaskId());
        for (TaskEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                log.error("Error in event listener: {}", e.getMessage(), e);
            }
        }
    }

    // Event classes
    public static class TaskSubmittedEvent extends TaskEvent {
        public TaskSubmittedEvent(Task task) { super(task); }
        @Override public EventType getEventType() { return EventType.SUBMITTED; }
    }

    public static class TaskStartedEvent extends TaskEvent {
        public TaskStartedEvent(Task task) { super(task); }
        @Override public EventType getEventType() { return EventType.STARTED; }
    }

    public static class TaskCompletedEvent extends TaskEvent {
        public TaskCompletedEvent(Task task) { super(task); }
        @Override public EventType getEventType() { return EventType.COMPLETED; }
    }

    public static class TaskFailedEvent extends TaskEvent {
        private final String error;
        public TaskFailedEvent(Task task, String error) { super(task); this.error = error; }
        @Override public EventType getEventType() { return EventType.FAILED; }
        public String getError() { return error; }
    }

    public static class TaskRetriedEvent extends TaskEvent {
        private final int attempt;
        public TaskRetriedEvent(Task task, int attempt) { super(task); this.attempt = attempt; }
        @Override public EventType getEventType() { return EventType.RETRIED; }
        public int getAttempt() { return attempt; }
    }

    public static class TaskCancelledEvent extends TaskEvent {
        public TaskCancelledEvent(Task task) { super(task); }
        @Override public EventType getEventType() { return EventType.CANCELLED; }
    }
}
