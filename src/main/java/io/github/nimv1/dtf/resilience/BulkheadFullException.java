package io.github.nimv1.dtf.resilience;

/**
 * Exception thrown when a bulkhead is full and rejects a request.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class BulkheadFullException extends RuntimeException {

    public BulkheadFullException(String message) {
        super(message);
    }

    public BulkheadFullException(String message, Throwable cause) {
        super(message, cause);
    }
}
