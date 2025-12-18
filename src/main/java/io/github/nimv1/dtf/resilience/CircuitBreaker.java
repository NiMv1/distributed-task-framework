package io.github.nimv1.dtf.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Circuit Breaker implementation for fault tolerance.
 * 
 * <p>States:</p>
 * <ul>
 *   <li>CLOSED - Normal operation, requests pass through</li>
 *   <li>OPEN - Circuit is open, requests fail fast</li>
 *   <li>HALF_OPEN - Testing if service recovered</li>
 * </ul>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final String name;
    private final int failureThreshold;
    private final int successThreshold;
    private final Duration waitDuration;
    
    private final AtomicReference<State> state;
    private final AtomicInteger failureCount;
    private final AtomicInteger successCount;
    private final AtomicReference<Instant> lastFailureTime;

    public CircuitBreaker(String name) {
        this(name, 5, 3, Duration.ofSeconds(30));
    }

    public CircuitBreaker(String name, int failureThreshold, int successThreshold, Duration waitDuration) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.waitDuration = waitDuration;
        this.state = new AtomicReference<>(State.CLOSED);
        this.failureCount = new AtomicInteger(0);
        this.successCount = new AtomicInteger(0);
        this.lastFailureTime = new AtomicReference<>(Instant.MIN);
        
        log.debug("CircuitBreaker '{}' created: failureThreshold={}, successThreshold={}, waitDuration={}",
                name, failureThreshold, successThreshold, waitDuration);
    }

    /**
     * Executes the supplier if circuit allows.
     *
     * @param supplier the operation to execute
     * @return the result
     * @throws CircuitBreakerOpenException if circuit is open
     */
    public <T> T execute(Supplier<T> supplier) {
        if (!allowRequest()) {
            throw new CircuitBreakerOpenException("CircuitBreaker '" + name + "' is OPEN");
        }

        try {
            T result = supplier.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }

    /**
     * Executes the runnable if circuit allows.
     *
     * @param runnable the operation to execute
     * @throws CircuitBreakerOpenException if circuit is open
     */
    public void execute(Runnable runnable) {
        execute(() -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Checks if a request is allowed through the circuit.
     */
    public boolean allowRequest() {
        State currentState = state.get();
        
        if (currentState == State.CLOSED) {
            return true;
        }
        
        if (currentState == State.OPEN) {
            if (shouldAttemptReset()) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("CircuitBreaker '{}' transitioning from OPEN to HALF_OPEN", name);
                    successCount.set(0);
                }
                return true;
            }
            return false;
        }
        
        // HALF_OPEN - allow limited requests
        return true;
    }

    private boolean shouldAttemptReset() {
        Instant lastFailure = lastFailureTime.get();
        return Duration.between(lastFailure, Instant.now()).compareTo(waitDuration) >= 0;
    }

    /**
     * Records a successful operation.
     */
    public void onSuccess() {
        State currentState = state.get();
        
        if (currentState == State.HALF_OPEN) {
            int successes = successCount.incrementAndGet();
            if (successes >= successThreshold) {
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    log.info("CircuitBreaker '{}' transitioning from HALF_OPEN to CLOSED", name);
                    failureCount.set(0);
                    successCount.set(0);
                }
            }
        } else if (currentState == State.CLOSED) {
            failureCount.set(0);
        }
    }

    /**
     * Records a failed operation.
     */
    public void onFailure() {
        lastFailureTime.set(Instant.now());
        State currentState = state.get();
        
        if (currentState == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                log.warn("CircuitBreaker '{}' transitioning from HALF_OPEN to OPEN", name);
            }
        } else if (currentState == State.CLOSED) {
            int failures = failureCount.incrementAndGet();
            if (failures >= failureThreshold) {
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    log.warn("CircuitBreaker '{}' transitioning from CLOSED to OPEN after {} failures", 
                            name, failures);
                }
            }
        }
    }

    /**
     * Resets the circuit breaker to CLOSED state.
     */
    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        successCount.set(0);
        log.info("CircuitBreaker '{}' reset to CLOSED", name);
    }

    /**
     * Forces the circuit breaker to OPEN state.
     */
    public void forceOpen() {
        state.set(State.OPEN);
        lastFailureTime.set(Instant.now());
        log.info("CircuitBreaker '{}' forced to OPEN", name);
    }

    public String getName() { return name; }
    public State getState() { return state.get(); }
    public int getFailureCount() { return failureCount.get(); }
    public int getSuccessCount() { return successCount.get(); }
    public int getFailureThreshold() { return failureThreshold; }
    public int getSuccessThreshold() { return successThreshold; }
    public Duration getWaitDuration() { return waitDuration; }

    @Override
    public String toString() {
        return "CircuitBreaker{name='" + name + "', state=" + state.get() + 
               ", failures=" + failureCount.get() + "/" + failureThreshold + "}";
    }
}
