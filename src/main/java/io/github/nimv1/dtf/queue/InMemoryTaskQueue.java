package io.github.nimv1.dtf.queue;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * In-memory implementation of TaskQueue for development and testing.
 * 
 * <p>Uses a priority queue to order tasks by priority level.</p>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class InMemoryTaskQueue implements TaskQueue {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTaskQueue.class);

    private final PriorityBlockingQueue<Task> queue;
    private final Map<String, Task> taskIndex;
    private final List<Task> deadLetterQueue;

    public InMemoryTaskQueue() {
        this.queue = new PriorityBlockingQueue<>(100, 
            Comparator.comparingInt((Task t) -> -t.getPriority().getValue())
                      .thenComparing(Task::getCreatedAt));
        this.taskIndex = new ConcurrentHashMap<>();
        this.deadLetterQueue = Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    public void submit(Task task) {
        taskIndex.put(task.getId(), task);
        queue.offer(task);
        log.debug("Task submitted: {}", task);
    }

    @Override
    public Optional<Task> poll() {
        Task task = queue.poll();
        if (task != null) {
            log.debug("Task polled: {}", task);
        }
        return Optional.ofNullable(task);
    }

    @Override
    public List<Task> pollBatch(int maxTasks) {
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < maxTasks; i++) {
            Task task = queue.poll();
            if (task == null) break;
            tasks.add(task);
        }
        log.debug("Polled {} tasks", tasks.size());
        return tasks;
    }

    @Override
    public long size() {
        return queue.size();
    }

    @Override
    public void acknowledge(Task task) {
        task.markCompleted();
        log.debug("Task acknowledged: {}", task.getId());
    }

    @Override
    public void retry(Task task) {
        if (task.canRetry()) {
            queue.offer(task);
            log.debug("Task queued for retry: {} (attempt {})", task.getId(), task.getRetryCount());
        } else {
            deadLetter(task);
        }
    }

    @Override
    public void deadLetter(Task task) {
        task.markFailed(task.getErrorMessage());
        deadLetterQueue.add(task);
        taskIndex.remove(task.getId());
        log.warn("Task moved to dead letter queue: {}", task.getId());
    }

    @Override
    public Optional<Task> getById(String taskId) {
        return Optional.ofNullable(taskIndex.get(taskId));
    }

    @Override
    public boolean cancel(String taskId) {
        Task task = taskIndex.get(taskId);
        if (task != null && task.getStatus() == TaskStatus.PENDING) {
            task.markCancelled();
            queue.remove(task);
            taskIndex.remove(taskId);
            log.debug("Task cancelled: {}", taskId);
            return true;
        }
        return false;
    }

    /**
     * Returns tasks in the dead letter queue.
     */
    public List<Task> getDeadLetterQueue() {
        return new ArrayList<>(deadLetterQueue);
    }
}
