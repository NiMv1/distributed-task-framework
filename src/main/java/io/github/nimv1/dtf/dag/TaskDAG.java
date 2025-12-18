package io.github.nimv1.dtf.dag;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskPriority;
import io.github.nimv1.dtf.queue.TaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Directed Acyclic Graph (DAG) for managing task dependencies.
 * 
 * <p>Allows defining complex workflows where tasks depend on the completion
 * of other tasks before they can be executed.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * TaskDAG dag = new TaskDAG("order-workflow", taskQueue);
 * 
 * dag.addNode("validate", "validate-order", payload);
 * dag.addNode("payment", "process-payment", payload);
 * dag.addNode("inventory", "reserve-inventory", payload);
 * dag.addNode("ship", "ship-order", payload);
 * 
 * dag.addDependency("payment", "validate");     // payment depends on validate
 * dag.addDependency("inventory", "validate");   // inventory depends on validate
 * dag.addDependency("ship", "payment");         // ship depends on payment
 * dag.addDependency("ship", "inventory");       // ship depends on inventory
 * 
 * dag.execute();  // Starts with "validate", then parallel "payment" and "inventory", then "ship"
 * }</pre>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TaskDAG {

    private static final Logger log = LoggerFactory.getLogger(TaskDAG.class);

    private final String id;
    private final TaskQueue taskQueue;
    private final Map<String, TaskNode> nodes;
    private final Map<String, String> taskIdToNodeId;
    private boolean started;
    private boolean completed;

    public TaskDAG(String id, TaskQueue taskQueue) {
        this.id = id;
        this.taskQueue = taskQueue;
        this.nodes = new ConcurrentHashMap<>();
        this.taskIdToNodeId = new ConcurrentHashMap<>();
        this.started = false;
        this.completed = false;
    }

    /**
     * Adds a node to the DAG.
     */
    public TaskDAG addNode(String nodeId, String taskType, Map<String, Object> payload) {
        return addNode(nodeId, taskType, payload, TaskPriority.NORMAL);
    }

    /**
     * Adds a node to the DAG with priority.
     */
    public TaskDAG addNode(String nodeId, String taskType, Map<String, Object> payload, TaskPriority priority) {
        if (started) {
            throw new IllegalStateException("Cannot add nodes after DAG has started");
        }
        nodes.put(nodeId, new TaskNode(nodeId, taskType, payload, priority));
        return this;
    }

    /**
     * Adds a dependency between nodes.
     * 
     * @param nodeId the node that depends on another
     * @param dependsOn the node that must complete first
     */
    public TaskDAG addDependency(String nodeId, String dependsOn) {
        if (started) {
            throw new IllegalStateException("Cannot add dependencies after DAG has started");
        }
        
        TaskNode node = nodes.get(nodeId);
        TaskNode dependency = nodes.get(dependsOn);
        
        if (node == null) {
            throw new IllegalArgumentException("Node not found: " + nodeId);
        }
        if (dependency == null) {
            throw new IllegalArgumentException("Dependency node not found: " + dependsOn);
        }
        
        node.addDependency(dependsOn);
        dependency.addDependent(nodeId);
        
        return this;
    }

    /**
     * Validates the DAG for cycles.
     */
    public boolean validate() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String nodeId : nodes.keySet()) {
            if (hasCycle(nodeId, visited, recursionStack)) {
                log.error("DAG {} contains a cycle involving node: {}", id, nodeId);
                return false;
            }
        }
        return true;
    }

    private boolean hasCycle(String nodeId, Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(nodeId)) {
            return true;
        }
        if (visited.contains(nodeId)) {
            return false;
        }
        
        visited.add(nodeId);
        recursionStack.add(nodeId);
        
        TaskNode node = nodes.get(nodeId);
        for (String dependent : node.getDependents()) {
            if (hasCycle(dependent, visited, recursionStack)) {
                return true;
            }
        }
        
        recursionStack.remove(nodeId);
        return false;
    }

    /**
     * Starts executing the DAG by submitting all ready tasks.
     */
    public void execute() {
        if (started) {
            throw new IllegalStateException("DAG has already started");
        }
        
        if (!validate()) {
            throw new IllegalStateException("DAG validation failed - contains cycles");
        }
        
        started = true;
        log.info("Starting DAG execution: {} with {} nodes", id, nodes.size());
        
        submitReadyTasks();
    }

    /**
     * Marks a task as completed and submits any newly ready tasks.
     */
    public void onTaskCompleted(String taskId) {
        String nodeId = taskIdToNodeId.get(taskId);
        if (nodeId == null) {
            log.warn("Unknown task completed: {}", taskId);
            return;
        }
        
        TaskNode node = nodes.get(nodeId);
        node.markCompleted();
        log.debug("DAG {} node completed: {}", id, nodeId);
        
        // Check if all nodes are completed
        if (nodes.values().stream().allMatch(TaskNode::isCompleted)) {
            completed = true;
            log.info("DAG {} completed successfully", id);
            return;
        }
        
        // Submit any tasks that are now ready
        submitReadyTasks();
    }

    private void submitReadyTasks() {
        List<TaskNode> readyNodes = nodes.values().stream()
                .filter(n -> !n.isCompleted())
                .filter(n -> n.getTaskId() == null)
                .filter(this::areDependenciesMet)
                .collect(Collectors.toList());
        
        for (TaskNode node : readyNodes) {
            Task task = node.toTask();
            node.setTaskId(task.getId());
            taskIdToNodeId.put(task.getId(), node.getId());
            taskQueue.submit(task);
            log.debug("DAG {} submitted task: {} (node: {})", id, task.getId(), node.getId());
        }
    }

    private boolean areDependenciesMet(TaskNode node) {
        return node.getDependencies().stream()
                .map(nodes::get)
                .allMatch(TaskNode::isCompleted);
    }

    /**
     * Returns the DAG ID.
     */
    public String getId() { return id; }

    /**
     * Returns whether the DAG has started.
     */
    public boolean isStarted() { return started; }

    /**
     * Returns whether the DAG has completed.
     */
    public boolean isCompleted() { return completed; }

    /**
     * Returns the number of nodes in the DAG.
     */
    public int getNodeCount() { return nodes.size(); }

    /**
     * Returns the number of completed nodes.
     */
    public int getCompletedCount() {
        return (int) nodes.values().stream().filter(TaskNode::isCompleted).count();
    }

    /**
     * Returns the progress as a percentage.
     */
    public double getProgress() {
        if (nodes.isEmpty()) return 100.0;
        return (getCompletedCount() * 100.0) / getNodeCount();
    }

    @Override
    public String toString() {
        return "TaskDAG{id='" + id + "', nodes=" + nodes.size() + 
               ", completed=" + getCompletedCount() + ", progress=" + String.format("%.1f%%", getProgress()) + "}";
    }
}
