package io.github.nimv1.dtf.batch;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.handler.TaskHandler;
import io.github.nimv1.dtf.core.TaskResult;
import io.github.nimv1.dtf.queue.TaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Processor for batch task execution.
 * 
 * <p>Supports:</p>
 * <ul>
 *   <li>Parallel batch processing</li>
 *   <li>Progress tracking</li>
 *   <li>Batch completion callbacks</li>
 * </ul>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class BatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(BatchProcessor.class);

    private final TaskQueue taskQueue;
    private final Map<String, TaskHandler> handlers;
    private final ExecutorService executor;
    private final Map<String, TaskBatch> activeBatches;
    private Consumer<TaskBatch> onBatchComplete;

    public BatchProcessor(TaskQueue taskQueue, int parallelism) {
        this.taskQueue = taskQueue;
        this.handlers = new ConcurrentHashMap<>();
        this.executor = Executors.newFixedThreadPool(parallelism);
        this.activeBatches = new ConcurrentHashMap<>();
    }

    public void registerHandler(String taskType, TaskHandler handler) {
        handlers.put(taskType, handler);
    }

    public void onBatchComplete(Consumer<TaskBatch> callback) {
        this.onBatchComplete = callback;
    }

    /**
     * Submits a batch of tasks for processing.
     *
     * @param batch the task batch
     * @return CompletableFuture that completes when batch is done
     */
    public CompletableFuture<TaskBatch> submitBatch(TaskBatch batch) {
        log.info("Submitting batch {} with {} tasks", batch.getBatchId(), batch.getTotalCount());
        activeBatches.put(batch.getBatchId(), batch);
        batch.start();

        List<CompletableFuture<Void>> futures = batch.getTasks().stream()
                .map(task -> CompletableFuture.runAsync(() -> processTask(batch, task), executor))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    log.info("Batch {} completed: {}", batch.getBatchId(), batch);
                    activeBatches.remove(batch.getBatchId());
                    if (onBatchComplete != null) {
                        onBatchComplete.accept(batch);
                    }
                    return batch;
                });
    }

    /**
     * Submits tasks as a new batch.
     */
    public CompletableFuture<TaskBatch> submitBatch(List<Task> tasks) {
        return submitBatch(new TaskBatch(tasks));
    }

    private void processTask(TaskBatch batch, Task task) {
        TaskHandler handler = handlers.get(task.getType());
        if (handler == null) {
            log.error("No handler for task type: {}", task.getType());
            batch.markTaskFailed();
            return;
        }

        try {
            TaskResult result = handler.handle(task);
            if (result.isSuccess()) {
                batch.markTaskCompleted();
            } else {
                batch.markTaskFailed();
            }
        } catch (Exception e) {
            log.error("Error processing task {}: {}", task.getId(), e.getMessage());
            batch.markTaskFailed();
        }
    }

    /**
     * Gets the progress of an active batch.
     */
    public double getBatchProgress(String batchId) {
        TaskBatch batch = activeBatches.get(batchId);
        return batch != null ? batch.getProgress() : -1;
    }

    /**
     * Gets an active batch by ID.
     */
    public TaskBatch getBatch(String batchId) {
        return activeBatches.get(batchId);
    }

    /**
     * Returns the number of active batches.
     */
    public int getActiveBatchCount() {
        return activeBatches.size();
    }

    /**
     * Shuts down the processor.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
