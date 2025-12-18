package io.github.nimv1.dtf.scheduler;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskPriority;
import io.github.nimv1.dtf.queue.TaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Scheduler for delayed and recurring tasks.
 * 
 * <p>Supports:</p>
 * <ul>
 *   <li>One-time delayed tasks</li>
 *   <li>Fixed-rate recurring tasks</li>
 *   <li>Fixed-delay recurring tasks</li>
 *   <li>Cron-like scheduling</li>
 * </ul>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);

    private final TaskQueue taskQueue;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks;

    public TaskScheduler(TaskQueue taskQueue) {
        this(taskQueue, 2);
    }

    public TaskScheduler(TaskQueue taskQueue, int poolSize) {
        this.taskQueue = taskQueue;
        this.scheduler = Executors.newScheduledThreadPool(poolSize);
        this.scheduledTasks = new ConcurrentHashMap<>();
    }

    /**
     * Schedules a task to be executed after a delay.
     *
     * @param taskType the task type
     * @param payload the task payload
     * @param delay the delay before execution
     * @return the scheduled task ID
     */
    public String schedule(String taskType, Map<String, Object> payload, Duration delay) {
        Task task = Task.builder(taskType)
                .payload(payload)
                .delay(delay)
                .build();

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            taskQueue.submit(task);
            log.debug("Delayed task submitted: {} (delay: {})", task.getId(), delay);
        }, delay.toMillis(), TimeUnit.MILLISECONDS);

        scheduledTasks.put(task.getId(), future);
        log.info("Task scheduled: {} to run after {}", task.getId(), delay);
        return task.getId();
    }

    /**
     * Schedules a task to be executed at a specific time.
     *
     * @param taskType the task type
     * @param payload the task payload
     * @param scheduledTime the time to execute
     * @return the scheduled task ID
     */
    public String scheduleAt(String taskType, Map<String, Object> payload, Instant scheduledTime) {
        Duration delay = Duration.between(Instant.now(), scheduledTime);
        if (delay.isNegative()) {
            delay = Duration.ZERO;
        }
        return schedule(taskType, payload, delay);
    }

    /**
     * Schedules a recurring task at a fixed rate.
     *
     * @param taskType the task type
     * @param payload the task payload
     * @param initialDelay the initial delay
     * @param period the period between executions
     * @return the schedule ID
     */
    public String scheduleAtFixedRate(String taskType, Map<String, Object> payload, 
                                       Duration initialDelay, Duration period) {
        String scheduleId = "schedule-" + System.currentTimeMillis();

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            Task task = Task.builder(taskType)
                    .payload(payload)
                    .build();
            taskQueue.submit(task);
            log.debug("Recurring task submitted: {} (schedule: {})", task.getId(), scheduleId);
        }, initialDelay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);

        scheduledTasks.put(scheduleId, future);
        log.info("Recurring task scheduled: {} (rate: {})", scheduleId, period);
        return scheduleId;
    }

    /**
     * Schedules a recurring task with a fixed delay between executions.
     *
     * @param taskType the task type
     * @param payload the task payload
     * @param initialDelay the initial delay
     * @param delay the delay between executions
     * @return the schedule ID
     */
    public String scheduleWithFixedDelay(String taskType, Map<String, Object> payload,
                                          Duration initialDelay, Duration delay) {
        String scheduleId = "schedule-" + System.currentTimeMillis();

        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(() -> {
            Task task = Task.builder(taskType)
                    .payload(payload)
                    .build();
            taskQueue.submit(task);
            log.debug("Recurring task submitted: {} (schedule: {})", task.getId(), scheduleId);
        }, initialDelay.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS);

        scheduledTasks.put(scheduleId, future);
        log.info("Recurring task scheduled: {} (delay: {})", scheduleId, delay);
        return scheduleId;
    }

    /**
     * Cancels a scheduled task.
     *
     * @param scheduleId the schedule ID
     * @return true if cancelled successfully
     */
    public boolean cancel(String scheduleId) {
        ScheduledFuture<?> future = scheduledTasks.remove(scheduleId);
        if (future != null) {
            boolean cancelled = future.cancel(false);
            log.info("Schedule cancelled: {} (success: {})", scheduleId, cancelled);
            return cancelled;
        }
        return false;
    }

    /**
     * Returns the number of active schedules.
     */
    public int getActiveScheduleCount() {
        return (int) scheduledTasks.values().stream()
                .filter(f -> !f.isDone() && !f.isCancelled())
                .count();
    }

    /**
     * Shuts down the scheduler.
     */
    public void shutdown() {
        log.info("Shutting down task scheduler...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
