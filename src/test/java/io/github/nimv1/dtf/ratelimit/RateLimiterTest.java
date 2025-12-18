package io.github.nimv1.dtf.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimiter.
 */
class RateLimiterTest {

    @Test
    void shouldCreateRateLimiter() {
        RateLimiter limiter = new RateLimiter(10.0);
        
        assertEquals(10.0, limiter.getPermitsPerSecond());
        assertTrue(limiter.getBurstCapacity() >= 1);
    }

    @Test
    void shouldCreateWithBurstCapacity() {
        RateLimiter limiter = new RateLimiter(10.0, 50);
        
        assertEquals(10.0, limiter.getPermitsPerSecond());
        assertEquals(50, limiter.getBurstCapacity());
    }

    @Test
    void shouldAcquirePermit() {
        RateLimiter limiter = new RateLimiter(100.0, 10);
        
        assertTrue(limiter.tryAcquire());
    }

    @Test
    void shouldTryAcquireWithTimeout() {
        RateLimiter limiter = new RateLimiter(100.0, 10);
        
        assertTrue(limiter.tryAcquire(100, TimeUnit.MILLISECONDS));
    }

    @Test
    void shouldRejectInvalidPermitsPerSecond() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(0));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(-1));
    }

    @Test
    void shouldRejectInvalidBurstCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(10.0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(10.0, -1));
    }

    @Test
    void shouldReturnAvailablePermits() {
        RateLimiter limiter = new RateLimiter(10.0, 5);
        
        int initial = limiter.availablePermits();
        assertTrue(initial > 0);
    }
}
