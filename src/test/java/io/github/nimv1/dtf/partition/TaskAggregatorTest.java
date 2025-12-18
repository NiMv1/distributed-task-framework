package io.github.nimv1.dtf.partition;

import io.github.nimv1.dtf.core.TaskResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TaskAggregator.
 */
class TaskAggregatorTest {

    private TaskAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new TaskAggregator();
    }

    @Test
    void shouldRegisterAggregationContext() {
        TaskAggregator.AggregationContext context = aggregator.register("parent-1", 5);

        assertNotNull(context);
        assertEquals("parent-1", context.getParentTaskId());
        assertEquals(5, context.getTotalPartitions());
        assertEquals(0, context.getCompletedCount());
        assertFalse(context.isComplete());
    }

    @Test
    void shouldAddResultsAndTrackCompletion() {
        aggregator.register("parent-1", 3);

        assertFalse(aggregator.addResult("parent-1", 0, TaskResult.success()));
        assertFalse(aggregator.addResult("parent-1", 1, TaskResult.success()));
        assertTrue(aggregator.addResult("parent-1", 2, TaskResult.success()));

        TaskAggregator.AggregationContext context = aggregator.getContext("parent-1");
        assertTrue(context.isComplete());
        assertEquals(3, context.getCompletedCount());
    }

    @Test
    void shouldReturnFalseForUnknownParent() {
        boolean result = aggregator.addResult("unknown", 0, TaskResult.success());
        assertFalse(result);
    }

    @Test
    void shouldCollectResultsInOrder() {
        aggregator.register("parent-1", 3);

        Map<String, Object> data0 = new HashMap<>();
        data0.put("value", "first");
        Map<String, Object> data1 = new HashMap<>();
        data1.put("value", "second");
        Map<String, Object> data2 = new HashMap<>();
        data2.put("value", "third");

        // Add out of order
        aggregator.addResult("parent-1", 2, TaskResult.success(data2));
        aggregator.addResult("parent-1", 0, TaskResult.success(data0));
        aggregator.addResult("parent-1", 1, TaskResult.success(data1));

        List<TaskResult> results = aggregator.collectResults("parent-1");

        assertEquals(3, results.size());
        assertEquals("first", results.get(0).getData().get("value"));
        assertEquals("second", results.get(1).getData().get("value"));
        assertEquals("third", results.get(2).getData().get("value"));
    }

    @Test
    void shouldReduceResults() {
        aggregator.register("parent-1", 3);

        Map<String, Object> data0 = new HashMap<>();
        data0.put("count", 10);
        Map<String, Object> data1 = new HashMap<>();
        data1.put("count", 20);
        Map<String, Object> data2 = new HashMap<>();
        data2.put("count", 30);

        aggregator.addResult("parent-1", 0, TaskResult.success(data0));
        aggregator.addResult("parent-1", 1, TaskResult.success(data1));
        aggregator.addResult("parent-1", 2, TaskResult.success(data2));

        int total = aggregator.reduceResults("parent-1", 0, (acc, result) -> {
            Object count = result.getData().get("count");
            return acc + ((Number) count).intValue();
        });

        assertEquals(60, total);
    }

    @Test
    void shouldMergeResultData() {
        aggregator.register("parent-1", 2);

        Map<String, Object> data0 = new HashMap<>();
        data0.put("key1", "value1");
        data0.put("key2", "value2");
        Map<String, Object> data1 = new HashMap<>();
        data1.put("key3", "value3");

        aggregator.addResult("parent-1", 0, TaskResult.success(data0));
        aggregator.addResult("parent-1", 1, TaskResult.success(data1));

        Map<String, Object> merged = aggregator.mergeResultData("parent-1");

        assertEquals(3, merged.size());
        assertEquals("value1", merged.get("key1"));
        assertEquals("value2", merged.get("key2"));
        assertEquals("value3", merged.get("key3"));
    }

    @Test
    void shouldCollectListData() {
        aggregator.register("parent-1", 3);

        Map<String, Object> data0 = new HashMap<>();
        data0.put("items", Arrays.asList(1, 2, 3));
        Map<String, Object> data1 = new HashMap<>();
        data1.put("items", Arrays.asList(4, 5));
        Map<String, Object> data2 = new HashMap<>();
        data2.put("items", Arrays.asList(6, 7, 8, 9));

        aggregator.addResult("parent-1", 0, TaskResult.success(data0));
        aggregator.addResult("parent-1", 1, TaskResult.success(data1));
        aggregator.addResult("parent-1", 2, TaskResult.success(data2));

        List<Integer> combined = aggregator.collectListData("parent-1", "items");

        assertEquals(9, combined.size());
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9), combined);
    }

    @Test
    void shouldSumNumericData() {
        aggregator.register("parent-1", 3);

        Map<String, Object> data0 = new HashMap<>();
        data0.put("processed", 100L);
        Map<String, Object> data1 = new HashMap<>();
        data1.put("processed", 250L);
        Map<String, Object> data2 = new HashMap<>();
        data2.put("processed", 150L);

        aggregator.addResult("parent-1", 0, TaskResult.success(data0));
        aggregator.addResult("parent-1", 1, TaskResult.success(data1));
        aggregator.addResult("parent-1", 2, TaskResult.success(data2));

        long sum = aggregator.sumNumericData("parent-1", "processed");

        assertEquals(500L, sum);
    }

    @Test
    void shouldCheckAllSucceeded() {
        aggregator.register("parent-1", 3);

        aggregator.addResult("parent-1", 0, TaskResult.success());
        aggregator.addResult("parent-1", 1, TaskResult.success());
        aggregator.addResult("parent-1", 2, TaskResult.success());

        assertTrue(aggregator.allSucceeded("parent-1"));
    }

    @Test
    void shouldDetectFailures() {
        aggregator.register("parent-1", 3);

        aggregator.addResult("parent-1", 0, TaskResult.success());
        aggregator.addResult("parent-1", 1, TaskResult.failure("Error 1"));
        aggregator.addResult("parent-1", 2, TaskResult.failure("Error 2"));

        assertFalse(aggregator.allSucceeded("parent-1"));
        assertEquals(2, aggregator.getFailedCount("parent-1"));
    }

    @Test
    void shouldTrackProgress() {
        aggregator.register("parent-1", 4);

        aggregator.addResult("parent-1", 0, TaskResult.success());
        TaskAggregator.AggregationContext context = aggregator.getContext("parent-1");
        assertEquals(0.25, context.getProgress(), 0.001);

        aggregator.addResult("parent-1", 1, TaskResult.success());
        assertEquals(0.5, context.getProgress(), 0.001);

        aggregator.addResult("parent-1", 2, TaskResult.success());
        assertEquals(0.75, context.getProgress(), 0.001);

        aggregator.addResult("parent-1", 3, TaskResult.success());
        assertEquals(1.0, context.getProgress(), 0.001);
    }

    @Test
    void shouldCleanupContext() {
        aggregator.register("parent-1", 2);
        aggregator.addResult("parent-1", 0, TaskResult.success());
        aggregator.addResult("parent-1", 1, TaskResult.success());

        assertNotNull(aggregator.getContext("parent-1"));

        aggregator.cleanup("parent-1");

        assertNull(aggregator.getContext("parent-1"));
    }

    @Test
    void shouldReturnEmptyForUnknownParentInCollect() {
        List<TaskResult> results = aggregator.collectResults("unknown");
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldReturnIdentityForUnknownParentInReduce() {
        String result = aggregator.reduceResults("unknown", "default", (acc, r) -> acc + "x");
        assertEquals("default", result);
    }

    @Test
    void shouldReturnEmptyMapForUnknownParentInMerge() {
        Map<String, Object> merged = aggregator.mergeResultData("unknown");
        assertTrue(merged.isEmpty());
    }

    @Test
    void shouldReturnZeroForUnknownParentInSum() {
        long sum = aggregator.sumNumericData("unknown", "key");
        assertEquals(0L, sum);
    }

    @Test
    void shouldGetSpecificPartitionResult() {
        aggregator.register("parent-1", 3);

        Map<String, Object> data1 = new HashMap<>();
        data1.put("partition", 1);

        aggregator.addResult("parent-1", 0, TaskResult.success());
        aggregator.addResult("parent-1", 1, TaskResult.success(data1));
        aggregator.addResult("parent-1", 2, TaskResult.success());

        TaskAggregator.AggregationContext context = aggregator.getContext("parent-1");
        TaskResult result = context.getResult(1);

        assertNotNull(result);
        assertEquals(1, result.getData().get("partition"));
    }
}
