package io.github.nimv1.dtf.client;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskPriority;
import io.github.nimv1.dtf.core.TaskStatus;
import io.github.nimv1.dtf.queue.TaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Client for submitting and managing distributed tasks.
 * 
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Autowired
 * private TaskClient taskClient;
 * 
 * public void sendEmail(String to, String subject) {
 *     taskClient.submit("send-email", Map.of(
 *         "to", to,
 *         "subject", subject
 *     ));
 * }
 * }</pre>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TaskClient {

    private static final Logger log = LoggerFactory.getLogger(TaskClient.class);

    private final TaskQueue taskQueue;

    public TaskClient(TaskQueue taskQueue) {
        this.taskQueue = taskQueue;
    }

    /**
     * Submits a task with default settings.
     * 
     * @param type task type
     * @param payload task data
     * @return the submitted task
     */
    public Task submit(String type, Map<String, Object> payload) {
        Task task = Task.builder(type)
                .payload(payload)
                .build();
        taskQueue.submit(task);
        log.info("Task submitted: {} (type: {})", task.getId(), type);
        return task;
    }

    /**
     * Submits a task with custom priority.
     * 
     * @param type task type
     * @param payload task data
     * @param priority task priority
     * @return the submitted task
     */
    public Task submit(String type, Map<String, Object> payload, TaskPriority priority) {
        Task task = Task.builder(type)
                .payload(payload)
                .priority(priority)
                .build();
        taskQueue.submit(task);
        log.info("Task submitted: {} (type: {}, priority: {})", task.getId(), type, priority);
        return task;
    }

    /**
     * Submits a fully configured task.
     * 
     * @param task the task to submit
     * @return the submitted task
     */
    public Task submit(Task task) {
        taskQueue.submit(task);
        log.info("Task submitted: {}", task);
        return task;
    }

    /**
     * Gets task status by ID.
     * 
     * @param taskId task ID
     * @return optional containing task status
     */
    public Optional<TaskStatus> getStatus(String taskId) {
        return taskQueue.getById(taskId).map(Task::getStatus);
    }

    /**
     * Gets task by ID.
     * 
     * @param taskId task ID
     * @return optional containing the task
     */
    public Optional<Task> getTask(String taskId) {
        return taskQueue.getById(taskId);
    }

    /**
     * Cancels a pending task.
     * 
     * @param taskId task ID
     * @return true if cancelled successfully
     */
    public boolean cancel(String taskId) {
        boolean cancelled = taskQueue.cancel(taskId);
        if (cancelled) {
            log.info("Task cancelled: {}", taskId);
        }
        return cancelled;
    }

    /**
     * Returns the number of pending tasks.
     * 
     * @return queue size
     */
    public long getPendingCount() {
        return taskQueue.size();
    }

    /**
     * Submits a task and returns a future that completes when the task is done.
     * Note: This polls for completion, use for testing/development only.
     * 
     * @param type task type
     * @param payload task data
     * @return future that completes with the task
     */
    public CompletableFuture<Task> submitAndWait(String type, Map<String, Object> payload) {
        Task task = submit(type, payload);
        return CompletableFuture.supplyAsync(() -> {
            while (true) {
                Optional<Task> current = getTask(task.getId());
                if (current.isEmpty()) {
                    throw new RuntimeException("Task not found: " + task.getId());
                }
                TaskStatus status = current.get().getStatus();
                if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED || status == TaskStatus.CANCELLED) {
                    return current.get();
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for task", e);
                }
            }
        });
    }
}
