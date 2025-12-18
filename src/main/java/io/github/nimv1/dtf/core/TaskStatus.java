package io.github.nimv1.dtf.core;

/**
 * Represents the lifecycle status of a task.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public enum TaskStatus {
    
    /**
     * Task is waiting to be picked up by a worker.
     */
    PENDING,
    
    /**
     * Task is currently being processed by a worker.
     */
    RUNNING,
    
    /**
     * Task completed successfully.
     */
    COMPLETED,
    
    /**
     * Task failed after exhausting all retries.
     */
    FAILED,
    
    /**
     * Task was cancelled before completion.
     */
    CANCELLED
}
