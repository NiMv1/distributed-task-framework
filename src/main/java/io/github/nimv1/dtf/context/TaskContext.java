package io.github.nimv1.dtf.context;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context for sharing data between tasks and across task execution.
 * 
 * <p>Thread-safe implementation using ConcurrentHashMap.</p>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TaskContext {

    private static final ThreadLocal<TaskContext> CURRENT = new ThreadLocal<>();

    private final String taskId;
    private final Map<String, Object> attributes;
    private final Map<String, String> headers;
    private TaskContext parent;

    public TaskContext(String taskId) {
        this.taskId = taskId;
        this.attributes = new ConcurrentHashMap<>();
        this.headers = new ConcurrentHashMap<>();
    }

    /**
     * Gets the current task context for this thread.
     */
    public static Optional<TaskContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * Sets the current task context for this thread.
     */
    public static void setCurrent(TaskContext context) {
        CURRENT.set(context);
    }

    /**
     * Clears the current task context.
     */
    public static void clear() {
        CURRENT.remove();
    }

    public String getTaskId() { return taskId; }

    /**
     * Sets an attribute in the context.
     */
    public TaskContext setAttribute(String key, Object value) {
        attributes.put(key, value);
        return this;
    }

    /**
     * Gets an attribute from the context.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getAttribute(String key) {
        return Optional.ofNullable((T) attributes.get(key));
    }

    /**
     * Gets an attribute with a default value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, T defaultValue) {
        return (T) attributes.getOrDefault(key, defaultValue);
    }

    /**
     * Removes an attribute.
     */
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    /**
     * Sets a header (for distributed tracing, etc.).
     */
    public TaskContext setHeader(String key, String value) {
        headers.put(key, value);
        return this;
    }

    /**
     * Gets a header.
     */
    public Optional<String> getHeader(String key) {
        return Optional.ofNullable(headers.get(key));
    }

    /**
     * Gets all headers.
     */
    public Map<String, String> getHeaders() {
        return Map.copyOf(headers);
    }

    /**
     * Gets all attributes.
     */
    public Map<String, Object> getAttributes() {
        return Map.copyOf(attributes);
    }

    /**
     * Sets the parent context (for task chaining).
     */
    public void setParent(TaskContext parent) {
        this.parent = parent;
    }

    /**
     * Gets the parent context.
     */
    public Optional<TaskContext> getParent() {
        return Optional.ofNullable(parent);
    }

    /**
     * Creates a child context inheriting headers from this context.
     */
    public TaskContext createChild(String childTaskId) {
        TaskContext child = new TaskContext(childTaskId);
        child.setParent(this);
        child.headers.putAll(this.headers);
        return child;
    }

    @Override
    public String toString() {
        return "TaskContext{taskId='" + taskId + "', attributes=" + attributes.size() + 
               ", headers=" + headers.size() + "}";
    }
}
