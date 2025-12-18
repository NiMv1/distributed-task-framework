package io.github.nimv1.dtf.partition;

import io.github.nimv1.dtf.core.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TaskPartitioner.
 */
class TaskPartitionerTest {

    private TaskPartitioner partitioner;

    @BeforeEach
    void setUp() {
        partitioner = new TaskPartitioner();
    }

    @Test
    void shouldPartitionListBySize() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("items", Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        
        Task task = Task.builder("process")
                .id("parent-1")
                .payload(payload)
                .build();

        List<Task> subtasks = partitioner.partitionByList(task, "items", 3);

        assertEquals(4, subtasks.size());
        
        // First partition: [1, 2, 3]
        List<?> items0 = (List<?>) subtasks.get(0).getPayload().get("items");
        assertEquals(3, items0.size());
        assertEquals(0, subtasks.get(0).getPayload().get("_partitionIndex"));
        
        // Last partition: [10]
        List<?> items3 = (List<?>) subtasks.get(3).getPayload().get("items");
        assertEquals(1, items3.size());
        assertEquals(3, subtasks.get(3).getPayload().get("_partitionIndex"));
    }

    @Test
    void shouldNotPartitionSmallList() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("items", Arrays.asList(1, 2, 3));
        
        Task task = Task.builder("process")
                .id("parent-1")
                .payload(payload)
                .build();

        List<Task> subtasks = partitioner.partitionByList(task, "items", 5);

        assertEquals(1, subtasks.size());
        assertSame(task, subtasks.get(0));
    }

    @Test
    void shouldReturnOriginalTaskForNonListPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("items", "not a list");
        
        Task task = Task.builder("process")
                .id("parent-1")
                .payload(payload)
                .build();

        List<Task> subtasks = partitioner.partitionByList(task, "items", 3);

        assertEquals(1, subtasks.size());
        assertSame(task, subtasks.get(0));
    }

    @Test
    void shouldPartitionByCount() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("data", Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        
        Task task = Task.builder("process")
                .id("parent-1")
                .payload(payload)
                .build();

        List<Task> subtasks = partitioner.partitionByCount(task, "data", 3);

        assertEquals(3, subtasks.size());
    }

    @Test
    void shouldPartitionRange() {
        Task task = Task.builder("process")
                .id("parent-1")
                .payload(new HashMap<>())
                .build();

        List<Task> subtasks = partitioner.partitionRange(task, 0, 100, 30);

        assertEquals(4, subtasks.size());
        
        // Check first partition range
        assertEquals(0L, subtasks.get(0).getPayload().get("_rangeStart"));
        assertEquals(30L, subtasks.get(0).getPayload().get("_rangeEnd"));
        
        // Check last partition range
        assertEquals(90L, subtasks.get(3).getPayload().get("_rangeStart"));
        assertEquals(100L, subtasks.get(3).getPayload().get("_rangeEnd"));
    }

    @Test
    void shouldReturnEmptyForInvalidRange() {
        Task task = Task.builder("process")
                .id("parent-1")
                .payload(new HashMap<>())
                .build();

        List<Task> subtasks = partitioner.partitionRange(task, 100, 50, 10);

        assertTrue(subtasks.isEmpty());
    }

    @Test
    void shouldPartitionWithCustomFunction() {
        Task task = Task.builder("process")
                .id("parent-1")
                .payload(new HashMap<>())
                .build();

        List<Task> subtasks = partitioner.partitionCustom(task, t -> {
            return Arrays.asList(
                    Task.builder(t.getType()).id("sub-1").payload(new HashMap<>()).build(),
                    Task.builder(t.getType()).id("sub-2").payload(new HashMap<>()).build()
            );
        });

        assertEquals(2, subtasks.size());
        assertEquals("sub-1", subtasks.get(0).getId());
        assertEquals("sub-2", subtasks.get(1).getId());
    }

    @Test
    void shouldIdentifyPartition() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("_parentTaskId", "parent-1");
        payload.put("_partitionIndex", 2);
        payload.put("_totalPartitions", 5L);
        
        Task partition = Task.builder("process")
                .id("sub-1")
                .payload(payload)
                .build();

        assertTrue(partitioner.isPartition(partition));
        assertEquals("parent-1", partitioner.getParentTaskId(partition));
        assertEquals(2, partitioner.getPartitionIndex(partition));
        assertEquals(5L, partitioner.getTotalPartitions(partition));
    }

    @Test
    void shouldNotIdentifyNonPartition() {
        Task task = Task.builder("process")
                .id("task-1")
                .payload(new HashMap<>())
                .build();

        assertFalse(partitioner.isPartition(task));
        assertNull(partitioner.getParentTaskId(task));
        assertEquals(-1, partitioner.getPartitionIndex(task));
        assertEquals(-1, partitioner.getTotalPartitions(task));
    }

    @Test
    void shouldPreserveTaskTypeAndPriority() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("items", Arrays.asList(1, 2, 3, 4, 5, 6));
        
        Task task = Task.builder("special-type")
                .id("parent-1")
                .payload(payload)
                .priority(io.github.nimv1.dtf.core.TaskPriority.HIGH)
                .build();

        List<Task> subtasks = partitioner.partitionByList(task, "items", 2);

        for (Task subtask : subtasks) {
            assertEquals("special-type", subtask.getType());
            assertEquals(io.github.nimv1.dtf.core.TaskPriority.HIGH, subtask.getPriority());
        }
    }

    @Test
    void shouldIncludePartitionMetadata() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("items", Arrays.asList(1, 2, 3, 4, 5));
        
        Task task = Task.builder("process")
                .id("parent-1")
                .payload(payload)
                .build();

        List<Task> subtasks = partitioner.partitionByList(task, "items", 2);

        assertEquals(3, subtasks.size());
        
        for (int i = 0; i < subtasks.size(); i++) {
            Task subtask = subtasks.get(i);
            assertEquals("parent-1", subtask.getPayload().get("_parentTaskId"));
            assertEquals(i, subtask.getPayload().get("_partitionIndex"));
            assertEquals(3, subtask.getPayload().get("_totalPartitions"));
        }
    }
}
