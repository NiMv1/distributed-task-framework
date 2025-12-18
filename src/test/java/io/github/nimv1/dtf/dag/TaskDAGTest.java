package io.github.nimv1.dtf.dag;

import io.github.nimv1.dtf.queue.InMemoryTaskQueue;
import io.github.nimv1.dtf.queue.TaskQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TaskDAG.
 * 
 * @author NiMv1
 */
class TaskDAGTest {

    private TaskQueue taskQueue;

    @BeforeEach
    void setUp() {
        taskQueue = new InMemoryTaskQueue();
    }

    @Test
    void shouldCreateDAGWithNodes() {
        TaskDAG dag = new TaskDAG("test-dag", taskQueue);
        
        dag.addNode("node1", "task-type-1", Map.of("key", "value1"));
        dag.addNode("node2", "task-type-2", Map.of("key", "value2"));
        
        assertEquals(2, dag.getNodeCount());
        assertFalse(dag.isStarted());
        assertFalse(dag.isCompleted());
    }

    @Test
    void shouldAddDependencies() {
        TaskDAG dag = new TaskDAG("test-dag", taskQueue);
        
        dag.addNode("validate", "validate-task", Map.of());
        dag.addNode("process", "process-task", Map.of());
        dag.addDependency("process", "validate");
        
        assertTrue(dag.validate());
    }

    @Test
    void shouldDetectCycles() {
        TaskDAG dag = new TaskDAG("cyclic-dag", taskQueue);
        
        dag.addNode("a", "task-a", Map.of());
        dag.addNode("b", "task-b", Map.of());
        dag.addNode("c", "task-c", Map.of());
        
        dag.addDependency("b", "a");
        dag.addDependency("c", "b");
        dag.addDependency("a", "c"); // Creates cycle: a -> b -> c -> a
        
        assertFalse(dag.validate());
    }

    @Test
    void shouldExecuteDAG() {
        TaskDAG dag = new TaskDAG("exec-dag", taskQueue);
        
        dag.addNode("start", "start-task", Map.of());
        dag.addNode("middle", "middle-task", Map.of());
        dag.addNode("end", "end-task", Map.of());
        
        dag.addDependency("middle", "start");
        dag.addDependency("end", "middle");
        
        dag.execute();
        
        assertTrue(dag.isStarted());
        assertEquals(1, taskQueue.size()); // Only "start" should be submitted initially
    }

    @Test
    void shouldTrackProgress() {
        TaskDAG dag = new TaskDAG("progress-dag", taskQueue);
        
        dag.addNode("task1", "type1", Map.of());
        dag.addNode("task2", "type2", Map.of());
        
        assertEquals(0.0, dag.getProgress());
        
        dag.execute();
        
        // Simulate task completion
        var task = taskQueue.poll();
        assertTrue(task.isPresent());
        dag.onTaskCompleted(task.get().getId());
        
        assertEquals(50.0, dag.getProgress());
    }

    @Test
    void shouldThrowOnCyclicDAGExecution() {
        TaskDAG dag = new TaskDAG("cyclic-dag", taskQueue);
        
        dag.addNode("a", "task-a", Map.of());
        dag.addNode("b", "task-b", Map.of());
        dag.addDependency("b", "a");
        dag.addDependency("a", "b"); // Creates cycle
        
        assertThrows(IllegalStateException.class, dag::execute);
    }

    @Test
    void shouldNotAllowModificationAfterStart() {
        TaskDAG dag = new TaskDAG("started-dag", taskQueue);
        dag.addNode("node1", "type1", Map.of());
        dag.execute();
        
        assertThrows(IllegalStateException.class, () -> 
            dag.addNode("node2", "type2", Map.of()));
    }
}
