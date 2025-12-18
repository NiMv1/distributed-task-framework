package io.github.nimv1.dtf.resilience;

/**
 * Exception thrown when a circuit breaker is open and rejects a request.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class CircuitBreakerOpenException extends RuntimeException {

    public CircuitBreakerOpenException(String message) {
        super(message);
    }

    public CircuitBreakerOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}
