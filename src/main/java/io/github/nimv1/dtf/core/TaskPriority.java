package io.github.nimv1.dtf.core;

/**
 * Task priority levels for queue ordering.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public enum TaskPriority {
    
    /**
     * Lowest priority, processed last.
     */
    LOW(0),
    
    /**
     * Default priority level.
     */
    NORMAL(5),
    
    /**
     * Higher priority, processed before normal tasks.
     */
    HIGH(10),
    
    /**
     * Highest priority, processed immediately.
     */
    CRITICAL(15);

    private final int value;

    TaskPriority(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
