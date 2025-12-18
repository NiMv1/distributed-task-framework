package io.github.nimv1.dtf.dag;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskPriority;

import java.util.*;

/**
 * Represents a node in a task dependency graph (DAG).
 * 
 * <p>Each node contains a task definition and references to its dependencies
 * (tasks that must complete before this task can run).</p>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TaskNode {

    private final String id;
    private final String taskType;
    private final Map<String, Object> payload;
    private final TaskPriority priority;
    private final Set<String> dependencies;
    private final Set<String> dependents;
    private boolean completed;
    private String taskId;

    public TaskNode(String id, String taskType, Map<String, Object> payload) {
        this(id, taskType, payload, TaskPriority.NORMAL);
    }

    public TaskNode(String id, String taskType, Map<String, Object> payload, TaskPriority priority) {
        this.id = id;
        this.taskType = taskType;
        this.payload = payload != null ? payload : Map.of();
        this.priority = priority;
        this.dependencies = new HashSet<>();
        this.dependents = new HashSet<>();
        this.completed = false;
    }

    public String getId() { return id; }
    public String getTaskType() { return taskType; }
    public Map<String, Object> getPayload() { return payload; }
    public TaskPriority getPriority() { return priority; }
    public Set<String> getDependencies() { return Collections.unmodifiableSet(dependencies); }
    public Set<String> getDependents() { return Collections.unmodifiableSet(dependents); }
    public boolean isCompleted() { return completed; }
    public String getTaskId() { return taskId; }

    public void addDependency(String nodeId) {
        dependencies.add(nodeId);
    }

    public void addDependent(String nodeId) {
        dependents.add(nodeId);
    }

    public void markCompleted() {
        this.completed = true;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public boolean isReady() {
        return dependencies.isEmpty() || dependencies.stream().allMatch(d -> false);
    }

    public Task toTask() {
        return Task.builder(taskType)
                .payload(payload)
                .priority(priority)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskNode taskNode = (TaskNode) o;
        return Objects.equals(id, taskNode.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TaskNode{id='" + id + "', taskType='" + taskType + 
               "', dependencies=" + dependencies + ", completed=" + completed + "}";
    }
}
