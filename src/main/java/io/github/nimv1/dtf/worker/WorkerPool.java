package io.github.nimv1.dtf.worker;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.handler.TaskHandler;
import io.github.nimv1.dtf.core.TaskResult;
import io.github.nimv1.dtf.core.TaskStatus;
import io.github.nimv1.dtf.event.TaskEventPublisher;
import io.github.nimv1.dtf.queue.TaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Worker pool for processing tasks from a queue.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class WorkerPool {

    private static final Logger log = LoggerFactory.getLogger(WorkerPool.class);

    private final String name;
    private final TaskQueue taskQueue;
    private final Map<String, TaskHandler> handlers;
    private final ExecutorService executor;
    private final int workerCount;
    private final AtomicBoolean running;
    private final AtomicLong processedCount;
    private final AtomicLong failedCount;
    private TaskEventPublisher eventPublisher;

    public WorkerPool(String name, TaskQueue taskQueue, int workerCount) {
        this.name = name;
        this.taskQueue = taskQueue;
        this.workerCount = workerCount;
        this.handlers = new ConcurrentHashMap<>();
        this.executor = Executors.newFixedThreadPool(workerCount);
        this.running = new AtomicBoolean(false);
        this.processedCount = new AtomicLong(0);
        this.failedCount = new AtomicLong(0);
        log.info("WorkerPool '{}' created with {} workers", name, workerCount);
    }

    public void registerHandler(String taskType, TaskHandler handler) {
        handlers.put(taskType, handler);
        log.debug("Registered handler for task type: {}", taskType);
    }

    public void setEventPublisher(TaskEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Starts the worker pool.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting WorkerPool '{}'", name);
            for (int i = 0; i < workerCount; i++) {
                final int workerId = i;
                executor.submit(() -> workerLoop(workerId));
            }
        }
    }

    /**
     * Stops the worker pool.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping WorkerPool '{}'", name);
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void workerLoop(int workerId) {
        log.debug("Worker {} started", workerId);
        while (running.get()) {
            try {
                Optional<Task> taskOpt = taskQueue.poll();
                if (taskOpt.isPresent()) {
                    processTask(taskOpt.get());
                } else {
                    Thread.sleep(100); // No tasks, wait a bit
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Worker {} error: {}", workerId, e.getMessage(), e);
            }
        }
        log.debug("Worker {} stopped", workerId);
    }

    private void processTask(Task task) {
        TaskHandler handler = handlers.get(task.getType());
        if (handler == null) {
            log.error("No handler for task type: {}", task.getType());
            taskQueue.deadLetter(task);
            failedCount.incrementAndGet();
            return;
        }

        try {
            if (eventPublisher != null) {
                eventPublisher.publishStarted(task);
            }

            TaskResult result = handler.handle(task);

            if (result.isSuccess()) {
                taskQueue.acknowledge(task);
                processedCount.incrementAndGet();
                if (eventPublisher != null) {
                    eventPublisher.publishCompleted(task);
                }
            } else {
                handleFailure(task, result.getErrorMessage());
            }
        } catch (Exception e) {
            handleFailure(task, e.getMessage());
        }
    }

    private void handleFailure(Task task, String error) {
        if (task.getRetryCount() < task.getMaxRetries()) {
            taskQueue.retry(task);
            if (eventPublisher != null) {
                eventPublisher.publishRetried(task, task.getRetryCount() + 1);
            }
        } else {
            taskQueue.deadLetter(task);
            failedCount.incrementAndGet();
            if (eventPublisher != null) {
                eventPublisher.publishFailed(task, error);
            }
        }
    }

    public String getName() { return name; }
    public int getWorkerCount() { return workerCount; }
    public boolean isRunning() { return running.get(); }
    public long getProcessedCount() { return processedCount.get(); }
    public long getFailedCount() { return failedCount.get(); }

    @Override
    public String toString() {
        return "WorkerPool{name='" + name + "', workers=" + workerCount + 
               ", running=" + running.get() + ", processed=" + processedCount.get() + "}";
    }
}
