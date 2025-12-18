package io.github.nimv1.dtf.core;

import java.io.Serializable;
import java.util.Map;

/**
 * Represents the result of task execution.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TaskResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final Map<String, Object> data;
    private final String errorMessage;
    private final Exception exception;

    private TaskResult(boolean success, Map<String, Object> data, String errorMessage, Exception exception) {
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
        this.exception = exception;
    }

    /**
     * Creates a successful result with data.
     */
    public static TaskResult success(Map<String, Object> data) {
        return new TaskResult(true, data, null, null);
    }

    /**
     * Creates a successful result without data.
     */
    public static TaskResult success() {
        return new TaskResult(true, Map.of(), null, null);
    }

    /**
     * Creates a failed result with error message.
     */
    public static TaskResult failure(String errorMessage) {
        return new TaskResult(false, Map.of(), errorMessage, null);
    }

    /**
     * Creates a failed result with exception.
     */
    public static TaskResult failure(Exception exception) {
        return new TaskResult(false, Map.of(), exception.getMessage(), exception);
    }

    public boolean isSuccess() { return success; }
    public Map<String, Object> getData() { return data; }
    public String getErrorMessage() { return errorMessage; }
    public Exception getException() { return exception; }

    @Override
    public String toString() {
        return "TaskResult{success=" + success + 
               (success ? ", data=" + data : ", error='" + errorMessage + "'") + "}";
    }
}
