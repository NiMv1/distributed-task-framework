package io.github.nimv1.dtf.resilience;

/**
 * Exception thrown when an operation exceeds its timeout.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TimeoutException extends RuntimeException {

    public TimeoutException(String message) {
        super(message);
    }

    public TimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
