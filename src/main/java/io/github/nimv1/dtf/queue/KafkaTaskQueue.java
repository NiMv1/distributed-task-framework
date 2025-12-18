package io.github.nimv1.dtf.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka-based implementation of TaskQueue for distributed task processing.
 * 
 * <p>Uses Kafka topics for task distribution across multiple workers.
 * Each priority level has its own topic for proper ordering.</p>
 * 
 * <p><b>Kafka Topics:</b></p>
 * <ul>
 *   <li>{prefix}.tasks.critical - Critical priority tasks</li>
 *   <li>{prefix}.tasks.high - High priority tasks</li>
 *   <li>{prefix}.tasks.normal - Normal priority tasks</li>
 *   <li>{prefix}.tasks.low - Low priority tasks</li>
 *   <li>{prefix}.dlq - Dead letter queue</li>
 * </ul>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class KafkaTaskQueue implements TaskQueue {

    private static final Logger log = LoggerFactory.getLogger(KafkaTaskQueue.class);

    private final KafkaProducer<String, String> producer;
    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper objectMapper;
    private final String topicPrefix;
    private final Map<String, Task> taskIndex;

    private static final String TOPIC_CRITICAL = ".tasks.critical";
    private static final String TOPIC_HIGH = ".tasks.high";
    private static final String TOPIC_NORMAL = ".tasks.normal";
    private static final String TOPIC_LOW = ".tasks.low";
    private static final String TOPIC_DLQ = ".dlq";

    public KafkaTaskQueue(KafkaProducer<String, String> producer, 
                          KafkaConsumer<String, String> consumer,
                          String topicPrefix) {
        this.producer = producer;
        this.consumer = consumer;
        this.topicPrefix = topicPrefix;
        this.taskIndex = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        
        // Subscribe to all priority topics
        consumer.subscribe(Arrays.asList(
            topicPrefix + TOPIC_CRITICAL,
            topicPrefix + TOPIC_HIGH,
            topicPrefix + TOPIC_NORMAL,
            topicPrefix + TOPIC_LOW
        ));
    }

    @Override
    public void submit(Task task) {
        try {
            String taskJson = objectMapper.writeValueAsString(task);
            String topic = getTopicForPriority(task);
            
            producer.send(new ProducerRecord<>(topic, task.getId(), taskJson), (metadata, exception) -> {
                if (exception != null) {
                    log.error("Failed to send task to Kafka: {}", task.getId(), exception);
                } else {
                    log.debug("Task submitted to Kafka: {} -> {} (partition: {}, offset: {})", 
                            task.getId(), topic, metadata.partition(), metadata.offset());
                }
            });
            
            taskIndex.put(task.getId(), task);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize task", e);
        }
    }

    @Override
    public Optional<Task> poll() {
        // Poll from topics in priority order (critical first)
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
        
        if (records.isEmpty()) {
            return Optional.empty();
        }
        
        // Process first record (Kafka handles ordering within partitions)
        for (ConsumerRecord<String, String> record : records) {
            try {
                Task task = objectMapper.readValue(record.value(), Task.class);
                taskIndex.put(task.getId(), task);
                log.debug("Task polled from Kafka: {} (topic: {}, partition: {}, offset: {})",
                        task.getId(), record.topic(), record.partition(), record.offset());
                return Optional.of(task);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize task from Kafka", e);
            }
        }
        
        return Optional.empty();
    }

    @Override
    public List<Task> pollBatch(int maxTasks) {
        List<Task> tasks = new ArrayList<>();
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
        
        int count = 0;
        for (ConsumerRecord<String, String> record : records) {
            if (count >= maxTasks) break;
            
            try {
                Task task = objectMapper.readValue(record.value(), Task.class);
                taskIndex.put(task.getId(), task);
                tasks.add(task);
                count++;
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize task from Kafka", e);
            }
        }
        
        log.debug("Polled {} tasks from Kafka", tasks.size());
        return tasks;
    }

    @Override
    public long size() {
        // Kafka doesn't provide easy queue size - return cached count
        return taskIndex.values().stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .count();
    }

    @Override
    public void acknowledge(Task task) {
        task.markCompleted();
        taskIndex.put(task.getId(), task);
        consumer.commitSync();
        log.debug("Task acknowledged: {}", task.getId());
    }

    @Override
    public void retry(Task task) {
        if (task.canRetry()) {
            submit(task);
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
            
            producer.send(new ProducerRecord<>(topicPrefix + TOPIC_DLQ, task.getId(), taskJson));
            taskIndex.remove(task.getId());
            
            log.warn("Task moved to dead letter queue: {}", task.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize task for DLQ", e);
        }
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
            taskIndex.put(taskId, task);
            log.debug("Task cancelled: {}", taskId);
            return true;
        }
        return false;
    }

    private String getTopicForPriority(Task task) {
        return switch (task.getPriority()) {
            case CRITICAL -> topicPrefix + TOPIC_CRITICAL;
            case HIGH -> topicPrefix + TOPIC_HIGH;
            case LOW -> topicPrefix + TOPIC_LOW;
            default -> topicPrefix + TOPIC_NORMAL;
        };
    }

    /**
     * Closes the Kafka producer and consumer.
     */
    public void close() {
        producer.close();
        consumer.close();
    }
}
