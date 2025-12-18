package io.github.nimv1.dtf.handler;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskResult;

/**
 * Interface for task handlers that process specific task types.
 * 
 * <p>Implement this interface to define how tasks of a specific type should be processed.</p>
 * 
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Component
 * public class EmailTaskHandler implements TaskHandler {
 *     
 *     @Override
 *     public String getTaskType() {
 *         return "send-email";
 *     }
 *     
 *     @Override
 *     public TaskResult handle(Task task) {
 *         String to = (String) task.getPayload().get("to");
 *         String subject = (String) task.getPayload().get("subject");
 *         // Send email...
 *         return TaskResult.success();
 *     }
 * }
 * }</pre>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public interface TaskHandler {

    /**
     * Returns the task type this handler processes.
     * 
     * @return task type identifier
     */
    String getTaskType();

    /**
     * Processes the given task.
     * 
     * @param task the task to process
     * @return result of task execution
     */
    TaskResult handle(Task task);

    /**
     * Called before task execution. Override for pre-processing logic.
     * 
     * @param task the task about to be executed
     */
    default void beforeHandle(Task task) {
        // Default: no-op
    }

    /**
     * Called after task execution. Override for post-processing logic.
     * 
     * @param task the executed task
     * @param result the execution result
     */
    default void afterHandle(Task task, TaskResult result) {
        // Default: no-op
    }
}
