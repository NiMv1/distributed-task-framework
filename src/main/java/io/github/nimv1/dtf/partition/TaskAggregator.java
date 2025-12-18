package io.github.nimv1.dtf.partition;

import io.github.nimv1.dtf.core.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Aggregates results from partitioned task executions.
 * Supports various aggregation strategies: collect, reduce, merge.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TaskAggregator {

    private static final Logger log = LoggerFactory.getLogger(TaskAggregator.class);

    private final Map<String, AggregationContext> contexts = new ConcurrentHashMap<>();

    /**
     * Registers a new aggregation context for a parent task.
     *
     * @param parentTaskId the parent task ID
     * @param totalPartitions expected number of partitions
     * @return the aggregation context
     */
    public AggregationContext register(String parentTaskId, int totalPartitions) {
        AggregationContext context = new AggregationContext(parentTaskId, totalPartitions);
        contexts.put(parentTaskId, context);
        log.info("Registered aggregation context for task {} with {} partitions", 
                parentTaskId, totalPartitions);
        return context;
    }

    /**
     * Adds a partition result to the aggregation.
     *
     * @param parentTaskId the parent task ID
     * @param partitionIndex the partition index
     * @param result the task result
     * @return true if all partitions are complete
     */
    public boolean addResult(String parentTaskId, int partitionIndex, TaskResult result) {
        AggregationContext context = contexts.get(parentTaskId);
        if (context == null) {
            log.warn("No aggregation context found for task {}", parentTaskId);
            return false;
        }

        context.addResult(partitionIndex, result);
        log.debug("Added result for partition {} of task {} ({}/{})", 
                partitionIndex, parentTaskId, context.getCompletedCount(), context.getTotalPartitions());

        return context.isComplete();
    }

    /**
     * Gets the aggregation context for a parent task.
     */
    public AggregationContext getContext(String parentTaskId) {
        return contexts.get(parentTaskId);
    }

    /**
     * Collects all results as a list.
     *
     * @param parentTaskId the parent task ID
     * @return list of all results in partition order
     */
    public List<TaskResult> collectResults(String parentTaskId) {
        AggregationContext context = contexts.get(parentTaskId);
        if (context == null) {
            return Collections.emptyList();
        }
        return context.getOrderedResults();
    }

    /**
     * Reduces all results using a reducer function.
     *
     * @param parentTaskId the parent task ID
     * @param identity the identity value
     * @param reducer the reducer function
     * @param <T> the result type
     * @return the reduced result
     */
    public <T> T reduceResults(String parentTaskId, T identity, 
            BiFunction<T, TaskResult, T> reducer) {
        AggregationContext context = contexts.get(parentTaskId);
        if (context == null) {
            return identity;
        }

        T result = identity;
        for (TaskResult taskResult : context.getOrderedResults()) {
            result = reducer.apply(result, taskResult);
        }
        return result;
    }

    /**
     * Merges all result data into a single map.
     *
     * @param parentTaskId the parent task ID
     * @return merged data from all results
     */
    public Map<String, Object> mergeResultData(String parentTaskId) {
        AggregationContext context = contexts.get(parentTaskId);
        if (context == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> merged = new HashMap<>();
        for (TaskResult result : context.getOrderedResults()) {
            if (result.getData() != null) {
                merged.putAll(result.getData());
            }
        }
        return merged;
    }

    /**
     * Collects list data from all partitions into a single list.
     *
     * @param parentTaskId the parent task ID
     * @param key the key containing list data in each result
     * @param <T> the element type
     * @return combined list from all partitions
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> collectListData(String parentTaskId, String key) {
        AggregationContext context = contexts.get(parentTaskId);
        if (context == null) {
            return Collections.emptyList();
        }

        List<T> combined = new ArrayList<>();
        for (TaskResult result : context.getOrderedResults()) {
            if (result.getData() != null) {
                Object value = result.getData().get(key);
                if (value instanceof List) {
                    combined.addAll((List<T>) value);
                }
            }
        }
        return combined;
    }

    /**
     * Sums numeric values from all partition results.
     *
     * @param parentTaskId the parent task ID
     * @param key the key containing numeric data
     * @return the sum
     */
    public long sumNumericData(String parentTaskId, String key) {
        AggregationContext context = contexts.get(parentTaskId);
        if (context == null) {
            return 0;
        }

        long sum = 0;
        for (TaskResult result : context.getOrderedResults()) {
            if (result.getData() != null) {
                Object value = result.getData().get(key);
                if (value instanceof Number) {
                    sum += ((Number) value).longValue();
                }
            }
        }
        return sum;
    }

    /**
     * Checks if all partitions completed successfully.
     *
     * @param parentTaskId the parent task ID
     * @return true if all partitions succeeded
     */
    public boolean allSucceeded(String parentTaskId) {
        AggregationContext context = contexts.get(parentTaskId);
        if (context == null || !context.isComplete()) {
            return false;
        }

        return context.getOrderedResults().stream()
                .allMatch(TaskResult::isSuccess);
    }

    /**
     * Gets the count of failed partitions.
     *
     * @param parentTaskId the parent task ID
     * @return number of failed partitions
     */
    public int getFailedCount(String parentTaskId) {
        AggregationContext context = contexts.get(parentTaskId);
        if (context == null) {
            return 0;
        }

        return (int) context.getOrderedResults().stream()
                .filter(r -> !r.isSuccess())
                .count();
    }

    /**
     * Removes the aggregation context after processing.
     *
     * @param parentTaskId the parent task ID
     */
    public void cleanup(String parentTaskId) {
        contexts.remove(parentTaskId);
        log.debug("Cleaned up aggregation context for task {}", parentTaskId);
    }

    /**
     * Context for tracking partition results.
     */
    public static class AggregationContext {
        private final String parentTaskId;
        private final int totalPartitions;
        private final Map<Integer, TaskResult> results = new ConcurrentHashMap<>();

        AggregationContext(String parentTaskId, int totalPartitions) {
            this.parentTaskId = parentTaskId;
            this.totalPartitions = totalPartitions;
        }

        void addResult(int partitionIndex, TaskResult result) {
            results.put(partitionIndex, result);
        }

        public String getParentTaskId() {
            return parentTaskId;
        }

        public int getTotalPartitions() {
            return totalPartitions;
        }

        public int getCompletedCount() {
            return results.size();
        }

        public boolean isComplete() {
            return results.size() >= totalPartitions;
        }

        public double getProgress() {
            return (double) results.size() / totalPartitions;
        }

        public List<TaskResult> getOrderedResults() {
            List<TaskResult> ordered = new ArrayList<>();
            for (int i = 0; i < totalPartitions; i++) {
                TaskResult result = results.get(i);
                if (result != null) {
                    ordered.add(result);
                }
            }
            return ordered;
        }

        public TaskResult getResult(int partitionIndex) {
            return results.get(partitionIndex);
        }
    }
}
