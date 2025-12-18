package io.github.nimv1.dtf.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskPriority;
import io.github.nimv1.dtf.core.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis-based implementation of TaskQueue for distributed task processing.
 * 
 * <p>Uses Redis Sorted Sets for priority-based ordering and Hash for task storage.</p>
 * 
 * <p><b>Redis Keys:</b></p>
 * <ul>
 *   <li>{prefix}:queue - Sorted set with task IDs ordered by priority</li>
 *   <li>{prefix}:tasks:{id} - Hash containing task data</li>
 *   <li>{prefix}:dlq - List of dead letter tasks</li>
 * </ul>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class RedisTaskQueue implements TaskQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisTaskQueue.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;

    private static final String QUEUE_KEY = ":queue";
    private static final String TASKS_KEY = ":tasks:";
    private static final String DLQ_KEY = ":dlq";
    private static final String PROCESSING_KEY = ":processing";

    public RedisTaskQueue(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void submit(Task task) {
        try {
            String taskJson = objectMapper.writeValueAsString(task);
            String taskKey = keyPrefix + TASKS_KEY + task.getId();
            
            // Store task data
            redisTemplate.opsForValue().set(taskKey, taskJson);
            
            // Add to priority queue (higher priority = higher score)
            double score = calculateScore(task);
            redisTemplate.opsForZSet().add(keyPrefix + QUEUE_KEY, task.getId(), score);
            
            log.debug("Task submitted to Redis: {} (priority: {})", task.getId(), task.getPriority());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize task", e);
        }
    }

    @Override
    public Optional<Task> poll() {
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        
        // Get highest priority task (highest score)
        Set<String> taskIds = zSetOps.reverseRange(keyPrefix + QUEUE_KEY, 0, 0);
        
        if (taskIds == null || taskIds.isEmpty()) {
            return Optional.empty();
        }
        
        String taskId = taskIds.iterator().next();
        
        // Atomically remove from queue and add to processing set
        Long removed = zSetOps.remove(keyPrefix + QUEUE_KEY, taskId);
        if (removed == null || removed == 0) {
            return Optional.empty(); // Another worker got it
        }
        
        redisTemplate.opsForSet().add(keyPrefix + PROCESSING_KEY, taskId);
        
        return getById(taskId);
    }

    @Override
    public List<Task> pollBatch(int maxTasks) {
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < maxTasks; i++) {
            Optional<Task> task = poll();
            if (task.isEmpty()) break;
            tasks.add(task.get());
        }
        return tasks;
    }

    @Override
    public long size() {
        Long size = redisTemplate.opsForZSet().size(keyPrefix + QUEUE_KEY);
        return size != null ? size : 0;
    }

    @Override
    public void acknowledge(Task task) {
        task.markCompleted();
        updateTask(task);
        redisTemplate.opsForSet().remove(keyPrefix + PROCESSING_KEY, task.getId());
        log.debug("Task acknowledged: {}", task.getId());
    }

    @Override
    public void retry(Task task) {
        if (task.canRetry()) {
            updateTask(task);
            double score = calculateScore(task);
            redisTemplate.opsForZSet().add(keyPrefix + QUEUE_KEY, task.getId(), score);
            redisTemplate.opsForSet().remove(keyPrefix + PROCESSING_KEY, task.getId());
            log.debug("Task queued for retry: {} (attempt {})", task.getId(), task.getRetryCount());
        } else {
            deadLetter(task);
        }
    }

    @Override
    public void deadLetter(Task task) {
        try {
            task.markFailed(task.getErrorMessage());
            String taskJson = objectMapper.writeValueAsString(task);
            
            redisTemplate.opsForList().rightPush(keyPrefix + DLQ_KEY, taskJson);
            redisTemplate.opsForSet().remove(keyPrefix + PROCESSING_KEY, task.getId());
            redisTemplate.delete(keyPrefix + TASKS_KEY + task.getId());
            
            log.warn("Task moved to dead letter queue: {}", task.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize task for DLQ", e);
        }
    }

    @Override
    public Optional<Task> getById(String taskId) {
        String taskJson = redisTemplate.opsForValue().get(keyPrefix + TASKS_KEY + taskId);
        if (taskJson == null) {
            return Optional.empty();
        }
        
        try {
            Task task = objectMapper.readValue(taskJson, Task.class);
            return Optional.of(task);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize task: {}", taskId, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean cancel(String taskId) {
        Optional<Task> taskOpt = getById(taskId);
        if (taskOpt.isEmpty()) {
            return false;
        }
        
        Task task = taskOpt.get();
        if (task.getStatus() != TaskStatus.PENDING) {
            return false;
        }
        
        Long removed = redisTemplate.opsForZSet().remove(keyPrefix + QUEUE_KEY, taskId);
        if (removed != null && removed > 0) {
            task.markCancelled();
            updateTask(task);
            log.debug("Task cancelled: {}", taskId);
            return true;
        }
        
        return false;
    }

    /**
     * Returns the number of tasks in the dead letter queue.
     */
    public long getDeadLetterQueueSize() {
        Long size = redisTemplate.opsForList().size(keyPrefix + DLQ_KEY);
        return size != null ? size : 0;
    }

    /**
     * Returns tasks from the dead letter queue.
     */
    public List<Task> getDeadLetterQueue(int limit) {
        List<String> taskJsons = redisTemplate.opsForList().range(keyPrefix + DLQ_KEY, 0, limit - 1);
        if (taskJsons == null) {
            return Collections.emptyList();
        }
        
        return taskJsons.stream()
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, Task.class);
                    } catch (JsonProcessingException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void updateTask(Task task) {
        try {
            String taskJson = objectMapper.writeValueAsString(task);
            redisTemplate.opsForValue().set(keyPrefix + TASKS_KEY + task.getId(), taskJson);
        } catch (JsonProcessingException e) {
            log.error("Failed to update task: {}", task.getId(), e);
        }
    }

    private double calculateScore(Task task) {
        // Higher priority = higher score, earlier creation = higher score within same priority
        return task.getPriority().getValue() * 1_000_000_000L + 
               (Long.MAX_VALUE - task.getCreatedAt().toEpochMilli());
    }
}
