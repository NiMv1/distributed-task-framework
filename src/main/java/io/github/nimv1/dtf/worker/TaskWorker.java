package io.github.nimv1.dtf.worker;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskResult;
import io.github.nimv1.dtf.handler.TaskHandler;
import io.github.nimv1.dtf.queue.TaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Worker that continuously polls and processes tasks from a queue.
 * Supports multiple handlers, graceful shutdown, and statistics tracking.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TaskWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TaskWorker.class);

    private final String workerId;
    private final TaskQueue taskQueue;
    private final Map<String, TaskHandler> handlers;
    private final AtomicBoolean running;
    private final AtomicLong processedCount;
    private final AtomicLong failedCount;
    private final long pollIntervalMs;
    
    private Thread workerThread;
    private Consumer<Task> onTaskComplete;
    private Consumer<Task> onTaskFailed;

    public TaskWorker(String workerId, TaskQueue taskQueue) {
        this(workerId, taskQueue, 100);
    }

    public TaskWorker(String workerId, TaskQueue taskQueue, long pollIntervalMs) {
        this.workerId = workerId;
        this.taskQueue = taskQueue;
        this.handlers = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(false);
        this.processedCount = new AtomicLong(0);
        this.failedCount = new AtomicLong(0);
        this.pollIntervalMs = pollIntervalMs;
    }

    /**
     * Creates a worker with a single handler.
     */
    public TaskWorker(String workerId, TaskQueue taskQueue, TaskHandler handler) {
        this(workerId, taskQueue);
        registerHandler(handler);
    }

    /**
     * Registers a task handler.
     */
    public void registerHandler(TaskHandler handler) {
        handlers.put(handler.getTaskType(), handler);
        log.debug("Worker {} registered handler for type: {}", workerId, handler.getTaskType());
    }

    /**
     * Sets callback for successful task completion.
     */
    public void onTaskComplete(Consumer<Task> callback) {
        this.onTaskComplete = callback;
    }

    /**
     * Sets callback for task failure.
     */
    public void onTaskFailed(Consumer<Task> callback) {
        this.onTaskFailed = callback;
    }

    /**
     * Starts the worker in a new thread.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            workerThread = new Thread(this, "worker-" + workerId);
            workerThread.start();
            log.info("Worker {} started", workerId);
        }
    }

    /**
     * Stops the worker gracefully.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Worker {} stopping...", workerId);
            if (workerThread != null) {
                workerThread.interrupt();
                try {
                    workerThread.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            log.info("Worker {} stopped. Processed: {}, Failed: {}", 
                    workerId, processedCount.get(), failedCount.get());
        }
    }

    @Override
    public void run() {
        log.debug("Worker {} entering run loop", workerId);
        
        while (running.get()) {
            try {
                var optTask = taskQueue.poll();
                
                if (optTask.isPresent()) {
                    Task task = optTask.get();
                    processTask(task);
                } else {
                    // No task available, wait before polling again
                    Thread.sleep(pollIntervalMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("Worker {} interrupted", workerId);
                break;
            } catch (Exception e) {
                log.error("Worker {} error: {}", workerId, e.getMessage(), e);
            }
        }
        
        log.debug("Worker {} exiting run loop", workerId);
    }

    private void processTask(Task task) {
        TaskHandler handler = handlers.get(task.getType());
        
        if (handler == null) {
            log.warn("Worker {}: No handler for task type '{}', moving to DLQ", 
                    workerId, task.getType());
            taskQueue.deadLetter(task);
            failedCount.incrementAndGet();
            return;
        }

        try {
            log.debug("Worker {} processing task: {}", workerId, task.getId());
            task.markStarted(workerId);
            
            handler.beforeHandle(task);
            TaskResult result = handler.handle(task);
            handler.afterHandle(task, result);

            if (result.isSuccess()) {
                task.markCompleted();
                taskQueue.acknowledge(task);
                processedCount.incrementAndGet();
                
                if (onTaskComplete != null) {
                    onTaskComplete.accept(task);
                }
                
                log.debug("Worker {} completed task: {}", workerId, task.getId());
            } else {
                handleFailure(task, result.getErrorMessage());
            }
        } catch (Exception e) {
            handleFailure(task, e.getMessage());
        }
    }

    private void handleFailure(Task task, String errorMessage) {
        log.warn("Worker {} task failed: {} - {}", workerId, task.getId(), errorMessage);
        task.markFailed(errorMessage);
        
        if (task.canRetry()) {
            taskQueue.retry(task);
            log.debug("Worker {} retrying task: {} (attempt {})", 
                    workerId, task.getId(), task.getRetryCount());
        } else {
            taskQueue.deadLetter(task);
            failedCount.incrementAndGet();
            
            if (onTaskFailed != null) {
                onTaskFailed.accept(task);
            }
        }
    }

    // Getters
    public String getWorkerId() { return workerId; }
    public boolean isRunning() { return running.get(); }
    public long getProcessedCount() { return processedCount.get(); }
    public long getFailedCount() { return failedCount.get(); }
    
    public WorkerStats getStats() {
        return new WorkerStats(workerId, running.get(), 
                processedCount.get(), failedCount.get());
    }

    /**
     * Worker statistics snapshot.
     */
    public record WorkerStats(
            String workerId,
            boolean running,
            long processedCount,
            long failedCount
    ) {
        public long totalCount() {
            return processedCount + failedCount;
        }
        
        public double successRate() {
            long total = totalCount();
            return total > 0 ? (double) processedCount / total : 0;
        }
    }
}
