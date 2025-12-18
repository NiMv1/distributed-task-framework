package io.github.nimv1.dtf.timeout;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Handles task execution timeouts.
 * Monitors running tasks and cancels them if they exceed their timeout.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TaskTimeoutHandler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TaskTimeoutHandler.class);

    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> timeoutFutures = new ConcurrentHashMap<>();
    private final Map<String, TaskTimeoutInfo> runningTasks = new ConcurrentHashMap<>();
    private Consumer<Task> onTimeoutCallback;

    public TaskTimeoutHandler() {
        this(Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "task-timeout-handler");
            t.setDaemon(true);
            return t;
        }));
    }

    public TaskTimeoutHandler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Registers a task for timeout monitoring.
     */
    public void registerTask(Task task) {
        if (task.getTimeoutMs() <= 0) {
            return;
        }

        TaskTimeoutInfo info = new TaskTimeoutInfo(task, Instant.now());
        runningTasks.put(task.getId(), info);

        ScheduledFuture<?> future = scheduler.schedule(
            () -> handleTimeout(task.getId()),
            task.getTimeoutMs(),
            TimeUnit.MILLISECONDS
        );
        timeoutFutures.put(task.getId(), future);

        log.debug("Task {} registered for timeout monitoring ({}ms)", task.getId(), task.getTimeoutMs());
    }

    /**
     * Unregisters a task from timeout monitoring (task completed normally).
     */
    public void unregisterTask(String taskId) {
        ScheduledFuture<?> future = timeoutFutures.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
        runningTasks.remove(taskId);
        log.debug("Task {} unregistered from timeout monitoring", taskId);
    }

    /**
     * Sets the callback to be invoked when a task times out.
     */
    public void setOnTimeoutCallback(Consumer<Task> callback) {
        this.onTimeoutCallback = callback;
    }

    /**
     * Checks if a task has timed out.
     */
    public boolean isTimedOut(String taskId) {
        TaskTimeoutInfo info = runningTasks.get(taskId);
        if (info == null) {
            return false;
        }
        long elapsed = Duration.between(info.getStartedAt(), Instant.now()).toMillis();
        return elapsed > info.getTask().getTimeoutMs();
    }

    /**
     * Returns the remaining time for a task in milliseconds.
     */
    public long getRemainingTime(String taskId) {
        TaskTimeoutInfo info = runningTasks.get(taskId);
        if (info == null) {
            return -1;
        }
        long elapsed = Duration.between(info.getStartedAt(), Instant.now()).toMillis();
        return Math.max(0, info.getTask().getTimeoutMs() - elapsed);
    }

    /**
     * Returns the number of tasks being monitored.
     */
    public int getMonitoredTaskCount() {
        return runningTasks.size();
    }

    private void handleTimeout(String taskId) {
        TaskTimeoutInfo info = runningTasks.remove(taskId);
        timeoutFutures.remove(taskId);

        if (info != null) {
            Task task = info.getTask();
            log.warn("Task {} timed out after {}ms", taskId, task.getTimeoutMs());
            
            if (onTimeoutCallback != null) {
                try {
                    onTimeoutCallback.accept(task);
                } catch (Exception e) {
                    log.error("Error in timeout callback for task {}", taskId, e);
                }
            }
        }
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        timeoutFutures.clear();
        runningTasks.clear();
        log.info("TaskTimeoutHandler closed");
    }

    /**
     * Information about a running task being monitored for timeout.
     */
    private static class TaskTimeoutInfo {
        private final Task task;
        private final Instant startedAt;

        public TaskTimeoutInfo(Task task, Instant startedAt) {
            this.task = task;
            this.startedAt = startedAt;
        }

        public Task getTask() { return task; }
        public Instant getStartedAt() { return startedAt; }
    }
}
