package io.github.nimv1.dtf.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.github.nimv1.dtf.core.TaskPriority;
import io.github.nimv1.dtf.queue.TaskQueue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer metrics for the Distributed Task Framework.
 * 
 * <p>Provides the following metrics:</p>
 * <ul>
 *   <li>dtf.tasks.submitted - Counter of submitted tasks</li>
 *   <li>dtf.tasks.completed - Counter of completed tasks</li>
 *   <li>dtf.tasks.failed - Counter of failed tasks</li>
 *   <li>dtf.tasks.retried - Counter of retried tasks</li>
 *   <li>dtf.tasks.queue.size - Gauge of current queue size</li>
 *   <li>dtf.tasks.processing.time - Timer of task processing duration</li>
 * </ul>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TaskMetrics {

    private final Counter tasksSubmitted;
    private final Counter tasksCompleted;
    private final Counter tasksFailed;
    private final Counter tasksRetried;
    private final Timer processingTime;
    private final AtomicLong activeWorkers;

    public TaskMetrics(MeterRegistry registry, TaskQueue taskQueue) {
        // Counters
        this.tasksSubmitted = Counter.builder("dtf.tasks.submitted")
                .description("Total number of tasks submitted")
                .register(registry);

        this.tasksCompleted = Counter.builder("dtf.tasks.completed")
                .description("Total number of tasks completed successfully")
                .register(registry);

        this.tasksFailed = Counter.builder("dtf.tasks.failed")
                .description("Total number of tasks that failed")
                .register(registry);

        this.tasksRetried = Counter.builder("dtf.tasks.retried")
                .description("Total number of task retries")
                .register(registry);

        // Timer
        this.processingTime = Timer.builder("dtf.tasks.processing.time")
                .description("Task processing duration")
                .register(registry);

        // Gauges
        this.activeWorkers = new AtomicLong(0);
        
        Gauge.builder("dtf.tasks.queue.size", taskQueue, TaskQueue::size)
                .description("Current number of tasks in queue")
                .register(registry);

        Gauge.builder("dtf.workers.active", activeWorkers, AtomicLong::get)
                .description("Number of active worker threads")
                .register(registry);
    }

    /**
     * Records a task submission.
     */
    public void recordSubmitted(TaskPriority priority) {
        tasksSubmitted.increment();
    }

    /**
     * Records a successful task completion.
     */
    public void recordCompleted() {
        tasksCompleted.increment();
    }

    /**
     * Records a task failure.
     */
    public void recordFailed() {
        tasksFailed.increment();
    }

    /**
     * Records a task retry.
     */
    public void recordRetry() {
        tasksRetried.increment();
    }

    /**
     * Records task processing time.
     */
    public void recordProcessingTime(long durationMs) {
        processingTime.record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Increments active worker count.
     */
    public void workerStarted() {
        activeWorkers.incrementAndGet();
    }

    /**
     * Decrements active worker count.
     */
    public void workerStopped() {
        activeWorkers.decrementAndGet();
    }

    /**
     * Returns the timer for manual recording.
     */
    public Timer getProcessingTimer() {
        return processingTime;
    }
}
