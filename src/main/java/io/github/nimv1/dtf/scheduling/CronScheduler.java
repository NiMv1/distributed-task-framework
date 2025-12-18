package io.github.nimv1.dtf.scheduling;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.queue.TaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Cron-like scheduler for recurring tasks.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class CronScheduler {

    private static final Logger log = LoggerFactory.getLogger(CronScheduler.class);

    private final TaskQueue taskQueue;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledJobs;

    public CronScheduler(TaskQueue taskQueue) {
        this(taskQueue, Executors.newScheduledThreadPool(2));
    }

    public CronScheduler(TaskQueue taskQueue, ScheduledExecutorService scheduler) {
        this.taskQueue = taskQueue;
        this.scheduler = scheduler;
        this.scheduledJobs = new ConcurrentHashMap<>();
    }

    /**
     * Schedules a task to run at fixed rate.
     *
     * @param jobId unique job identifier
     * @param taskSupplier supplier that creates the task
     * @param initialDelay initial delay before first execution
     * @param period period between executions
     * @return the job ID
     */
    public String scheduleAtFixedRate(String jobId, Supplier<Task> taskSupplier, 
                                       Duration initialDelay, Duration period) {
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                Task task = taskSupplier.get();
                taskQueue.submit(task);
                log.debug("Scheduled job {} submitted task {}", jobId, task.getId());
            } catch (Exception e) {
                log.error("Error in scheduled job {}: {}", jobId, e.getMessage());
            }
        }, initialDelay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);

        scheduledJobs.put(jobId, future);
        log.info("Job {} scheduled with period {}", jobId, period);
        return jobId;
    }

    /**
     * Schedules a task to run with fixed delay between executions.
     */
    public String scheduleWithFixedDelay(String jobId, Supplier<Task> taskSupplier,
                                          Duration initialDelay, Duration delay) {
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(() -> {
            try {
                Task task = taskSupplier.get();
                taskQueue.submit(task);
                log.debug("Scheduled job {} submitted task {}", jobId, task.getId());
            } catch (Exception e) {
                log.error("Error in scheduled job {}: {}", jobId, e.getMessage());
            }
        }, initialDelay.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS);

        scheduledJobs.put(jobId, future);
        log.info("Job {} scheduled with delay {}", jobId, delay);
        return jobId;
    }

    /**
     * Schedules a one-time task execution.
     */
    public String scheduleOnce(String jobId, Supplier<Task> taskSupplier, Duration delay) {
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                Task task = taskSupplier.get();
                taskQueue.submit(task);
                log.debug("One-time job {} submitted task {}", jobId, task.getId());
                scheduledJobs.remove(jobId);
            } catch (Exception e) {
                log.error("Error in one-time job {}: {}", jobId, e.getMessage());
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);

        scheduledJobs.put(jobId, future);
        log.info("One-time job {} scheduled for {}", jobId, Instant.now().plus(delay));
        return jobId;
    }

    /**
     * Cancels a scheduled job.
     */
    public boolean cancel(String jobId) {
        ScheduledFuture<?> future = scheduledJobs.remove(jobId);
        if (future != null) {
            future.cancel(false);
            log.info("Job {} cancelled", jobId);
            return true;
        }
        return false;
    }

    /**
     * Returns the number of active scheduled jobs.
     */
    public int getActiveJobCount() {
        return scheduledJobs.size();
    }

    /**
     * Checks if a job is scheduled.
     */
    public boolean isScheduled(String jobId) {
        return scheduledJobs.containsKey(jobId);
    }

    /**
     * Shuts down the scheduler.
     */
    public void shutdown() {
        scheduledJobs.values().forEach(f -> f.cancel(false));
        scheduledJobs.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("CronScheduler shut down");
    }
}
