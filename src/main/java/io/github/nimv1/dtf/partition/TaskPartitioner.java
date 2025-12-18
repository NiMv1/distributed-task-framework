package io.github.nimv1.dtf.partition;

import io.github.nimv1.dtf.core.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Partitions large tasks into smaller subtasks for parallel processing.
 * Supports various partitioning strategies: by count, by size, or custom.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TaskPartitioner {

    private static final Logger log = LoggerFactory.getLogger(TaskPartitioner.class);

    /**
     * Partitions a task into subtasks by splitting a list payload.
     *
     * @param task the parent task
     * @param listKey the key in payload containing the list to split
     * @param partitionSize maximum items per partition
     * @return list of subtasks
     */
    @SuppressWarnings("unchecked")
    public List<Task> partitionByList(Task task, String listKey, int partitionSize) {
        Object listObj = task.getPayload().get(listKey);
        if (!(listObj instanceof List)) {
            log.warn("Payload key '{}' is not a List, returning original task", listKey);
            return List.of(task);
        }

        List<?> items = (List<?>) listObj;
        if (items.size() <= partitionSize) {
            log.debug("List size {} <= partition size {}, no partitioning needed", 
                    items.size(), partitionSize);
            return List.of(task);
        }

        List<Task> subtasks = new ArrayList<>();
        String parentId = task.getId();
        int partitionIndex = 0;

        for (int i = 0; i < items.size(); i += partitionSize) {
            int end = Math.min(i + partitionSize, items.size());
            List<?> partition = items.subList(i, end);

            Map<String, Object> subtaskPayload = new java.util.HashMap<>(task.getPayload());
            subtaskPayload.put(listKey, new ArrayList<>(partition));
            subtaskPayload.put("_partitionIndex", partitionIndex);
            subtaskPayload.put("_parentTaskId", parentId);
            subtaskPayload.put("_totalPartitions", (int) Math.ceil((double) items.size() / partitionSize));

            Task subtask = Task.builder(task.getType())
                    .id(UUID.randomUUID().toString())
                    .payload(subtaskPayload)
                    .priority(task.getPriority())
                    .build();

            subtasks.add(subtask);
            partitionIndex++;
        }

        log.info("Partitioned task {} into {} subtasks (partition size: {})", 
                parentId, subtasks.size(), partitionSize);
        return subtasks;
    }

    /**
     * Partitions a task into a fixed number of subtasks.
     *
     * @param task the parent task
     * @param listKey the key in payload containing the list to split
     * @param numPartitions number of partitions to create
     * @return list of subtasks
     */
    @SuppressWarnings("unchecked")
    public List<Task> partitionByCount(Task task, String listKey, int numPartitions) {
        Object listObj = task.getPayload().get(listKey);
        if (!(listObj instanceof List)) {
            log.warn("Payload key '{}' is not a List, returning original task", listKey);
            return List.of(task);
        }

        List<?> items = (List<?>) listObj;
        int partitionSize = (int) Math.ceil((double) items.size() / numPartitions);
        return partitionByList(task, listKey, partitionSize);
    }

    /**
     * Partitions a numeric range into subtasks.
     *
     * @param task the parent task
     * @param start range start (inclusive)
     * @param end range end (exclusive)
     * @param partitionSize items per partition
     * @return list of subtasks
     */
    public List<Task> partitionRange(Task task, long start, long end, long partitionSize) {
        if (end <= start) {
            log.warn("Invalid range [{}, {}), returning empty list", start, end);
            return List.of();
        }

        List<Task> subtasks = new ArrayList<>();
        String parentId = task.getId();
        int partitionIndex = 0;
        long totalPartitions = (long) Math.ceil((double) (end - start) / partitionSize);

        for (long i = start; i < end; i += partitionSize) {
            long partitionEnd = Math.min(i + partitionSize, end);

            Map<String, Object> subtaskPayload = new java.util.HashMap<>(task.getPayload());
            subtaskPayload.put("_rangeStart", i);
            subtaskPayload.put("_rangeEnd", partitionEnd);
            subtaskPayload.put("_partitionIndex", partitionIndex);
            subtaskPayload.put("_parentTaskId", parentId);
            subtaskPayload.put("_totalPartitions", totalPartitions);

            Task subtask = Task.builder(task.getType())
                    .id(UUID.randomUUID().toString())
                    .payload(subtaskPayload)
                    .priority(task.getPriority())
                    .build();

            subtasks.add(subtask);
            partitionIndex++;
        }

        log.info("Partitioned range [{}, {}) into {} subtasks", start, end, subtasks.size());
        return subtasks;
    }

    /**
     * Partitions a task using a custom partitioning function.
     *
     * @param task the parent task
     * @param partitioner custom function that creates subtasks
     * @return list of subtasks
     */
    public List<Task> partitionCustom(Task task, Function<Task, List<Task>> partitioner) {
        List<Task> subtasks = partitioner.apply(task);
        log.info("Custom partitioned task {} into {} subtasks", task.getId(), subtasks.size());
        return subtasks;
    }

    /**
     * Checks if a task is a partition (subtask).
     */
    public boolean isPartition(Task task) {
        return task.getPayload().containsKey("_parentTaskId");
    }

    /**
     * Gets the parent task ID from a partition.
     */
    public String getParentTaskId(Task task) {
        return (String) task.getPayload().get("_parentTaskId");
    }

    /**
     * Gets the partition index from a partition.
     */
    public int getPartitionIndex(Task task) {
        Object index = task.getPayload().get("_partitionIndex");
        return index instanceof Number ? ((Number) index).intValue() : -1;
    }

    /**
     * Gets the total number of partitions from a partition.
     */
    public long getTotalPartitions(Task task) {
        Object total = task.getPayload().get("_totalPartitions");
        return total instanceof Number ? ((Number) total).longValue() : -1;
    }
}
