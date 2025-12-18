package io.github.nimv1.dtf.worker;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskResult;
import io.github.nimv1.dtf.handler.TaskHandler;
import io.github.nimv1.dtf.queue.TaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Worker that processes tasks from the queue.
 * 
 * <p>Workers poll tasks from the queue and delegate processing to appropriate handlers.</p>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class Worker {

    private static final Logger log = LoggerFactory.getLogger(Worker.class);

    private final String workerId;
    private final TaskQueue taskQueue;
    private final Map<String, TaskHandler> handlers;
    private final ExecutorService executor;
    private final AtomicBoolean running;
    private final int concurrency;

    public Worker(TaskQueue taskQueue, Map<String, TaskHandler> handlers, int concurrency) {
        this.workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
        this.taskQueue = taskQueue;
        this.handlers = handlers;
        this.concurrency = concurrency;
        this.executor = Executors.newFixedThreadPool(concurrency);
        this.running = new AtomicBoolean(false);
    }

    /**
     * Starts the worker.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Worker {} starting with {} threads", workerId, concurrency);
            for (int i = 0; i < concurrency; i++) {
                executor.submit(this::processLoop);
            }
        }
    }

    /**
     * Stops the worker gracefully.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Worker {} stopping", workerId);
            executor.shutdown();
        }
    }

    private void processLoop() {
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
                log.error("Error in process loop", e);
            }
        }
    }

    private void processTask(Task task) {
        TaskHandler handler = handlers.get(task.getType());
        
        if (handler == null) {
            log.error("No handler found for task type: {}", task.getType());
            task.markFailed("No handler found for task type: " + task.getType());
            taskQueue.deadLetter(task);
            return;
        }

        task.markStarted(workerId);
        log.info("Processing task: {} (type: {})", task.getId(), task.getType());

        try {
            handler.beforeHandle(task);
            TaskResult result = handler.handle(task);
            handler.afterHandle(task, result);

            if (result.isSuccess()) {
                taskQueue.acknowledge(task);
                log.info("Task completed successfully: {}", task.getId());
            } else {
                handleFailure(task, result.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Task execution failed: {}", task.getId(), e);
            handleFailure(task, e.getMessage());
        }
    }

    private void handleFailure(Task task, String errorMessage) {
        task.markFailed(errorMessage);
        if (task.canRetry()) {
            taskQueue.retry(task);
            log.warn("Task {} will be retried (attempt {}/{})", 
                    task.getId(), task.getRetryCount(), task.getMaxRetries());
        } else {
            taskQueue.deadLetter(task);
            log.error("Task {} moved to dead letter queue after {} retries", 
                    task.getId(), task.getMaxRetries());
        }
    }

    public String getWorkerId() {
        return workerId;
    }

    public boolean isRunning() {
        return running.get();
    }
}
