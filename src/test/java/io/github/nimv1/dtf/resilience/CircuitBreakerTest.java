package io.github.nimv1.dtf.resilience;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CircuitBreaker.
 */
class CircuitBreakerTest {

    @Test
    void shouldStartInClosedState() {
        CircuitBreaker cb = new CircuitBreaker("test");
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void shouldOpenAfterFailureThreshold() {
        CircuitBreaker cb = new CircuitBreaker("test", 3, 2, Duration.ofSeconds(1));
        
        for (int i = 0; i < 3; i++) {
            cb.onFailure();
        }
        
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    void shouldRejectRequestsWhenOpen() {
        CircuitBreaker cb = new CircuitBreaker("test", 1, 1, Duration.ofMinutes(1));
        cb.onFailure();
        
        assertThrows(CircuitBreakerOpenException.class, () -> 
            cb.execute(() -> "test"));
    }

    @Test
    void shouldExecuteSuccessfully() {
        CircuitBreaker cb = new CircuitBreaker("test");
        
        String result = cb.execute(() -> "success");
        
        assertEquals("success", result);
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void shouldResetFailureCountOnSuccess() {
        CircuitBreaker cb = new CircuitBreaker("test", 3, 2, Duration.ofSeconds(1));
        
        cb.onFailure();
        cb.onFailure();
        assertEquals(2, cb.getFailureCount());
        
        cb.onSuccess();
        assertEquals(0, cb.getFailureCount());
    }

    @Test
    void shouldTransitionToClosedAfterSuccessThreshold() {
        CircuitBreaker cb = new CircuitBreaker("test", 1, 2, Duration.ofMillis(1));
        cb.forceOpen();
        
        // Wait for half-open transition
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        
        cb.allowRequest(); // Triggers HALF_OPEN
        cb.onSuccess();
        cb.onSuccess();
        
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void shouldResetCircuitBreaker() {
        CircuitBreaker cb = new CircuitBreaker("test", 1, 1, Duration.ofMinutes(1));
        cb.forceOpen();
        
        cb.reset();
        
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertEquals(0, cb.getFailureCount());
    }
}
