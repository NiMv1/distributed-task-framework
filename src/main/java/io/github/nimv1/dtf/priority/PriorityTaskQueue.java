package io.github.nimv1.dtf.priority;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskPriority;
import io.github.nimv1.dtf.queue.TaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Priority-based task queue implementation.
 * 
 * <p>Tasks are ordered by priority (CRITICAL > HIGH > NORMAL > LOW).</p>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class PriorityTaskQueue implements TaskQueue {

    private static final Logger log = LoggerFactory.getLogger(PriorityTaskQueue.class);

    private final PriorityBlockingQueue<Task> queue;
    private final Map<String, Task> taskIndex;
    private final Queue<Task> deadLetterQueue;

    public PriorityTaskQueue() {
        // Higher value = higher priority (CRITICAL=15, HIGH=10, NORMAL=5, LOW=0)
        // Negate to get descending order
        this.queue = new PriorityBlockingQueue<>(100, 
                Comparator.comparingInt((Task t) -> -t.getPriority().getValue()));
        this.taskIndex = new ConcurrentHashMap<>();
        this.deadLetterQueue = new LinkedList<>();
    }

    @Override
    public void submit(Task task) {
        queue.offer(task);
        taskIndex.put(task.getId(), task);
        log.debug("Task {} submitted with priority {}", task.getId(), task.getPriority());
    }

    @Override
    public Optional<Task> poll() {
        Task task = queue.poll();
        if (task != null) {
            taskIndex.remove(task.getId());
        }
        return Optional.ofNullable(task);
    }

    @Override
    public void acknowledge(Task task) {
        taskIndex.remove(task.getId());
        log.debug("Task {} acknowledged", task.getId());
    }

    @Override
    public void retry(Task task) {
        queue.offer(task);
        taskIndex.put(task.getId(), task);
        log.debug("Task {} scheduled for retry (attempt {})", task.getId(), task.getRetryCount());
    }

    @Override
    public List<Task> pollBatch(int maxTasks) {
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < maxTasks; i++) {
            Task task = queue.poll();
            if (task == null) break;
            taskIndex.remove(task.getId());
            tasks.add(task);
        }
        return tasks;
    }

    @Override
    public boolean cancel(String taskId) {
        Task task = taskIndex.remove(taskId);
        if (task != null) {
            queue.remove(task);
            log.debug("Task {} cancelled", taskId);
            return true;
        }
        return false;
    }

    @Override
    public void deadLetter(Task task) {
        taskIndex.remove(task.getId());
        deadLetterQueue.offer(task);
        log.warn("Task {} moved to dead letter queue", task.getId());
    }

    @Override
    public long size() {
        return queue.size();
    }

    @Override
    public Optional<Task> getById(String taskId) {
        return Optional.ofNullable(taskIndex.get(taskId));
    }

    /**
     * Returns the count of tasks by priority.
     */
    public Map<TaskPriority, Long> countByPriority() {
        Map<TaskPriority, Long> counts = new EnumMap<>(TaskPriority.class);
        for (TaskPriority priority : TaskPriority.values()) {
            counts.put(priority, 0L);
        }
        for (Task task : queue) {
            counts.merge(task.getPriority(), 1L, Long::sum);
        }
        return counts;
    }

    /**
     * Returns the dead letter queue size.
     */
    public int deadLetterSize() {
        return deadLetterQueue.size();
    }

    /**
     * Polls from dead letter queue.
     */
    public Optional<Task> pollDeadLetter() {
        return Optional.ofNullable(deadLetterQueue.poll());
    }
}
