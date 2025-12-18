package io.github.nimv1.dtf.retry;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RetryPolicy.
 */
class RetryPolicyTest {

    @Test
    void shouldCreateDefaultPolicy() {
        RetryPolicy policy = RetryPolicy.defaultPolicy();
        
        assertEquals(3, policy.getMaxRetries());
        assertEquals(Duration.ofSeconds(1), policy.getInitialDelay());
        assertFalse(policy.isExponentialBackoff());
    }

    @Test
    void shouldCreateNoRetryPolicy() {
        RetryPolicy policy = RetryPolicy.noRetry();
        
        assertEquals(0, policy.getMaxRetries());
        assertFalse(policy.shouldRetry(new RuntimeException(), 0));
    }

    @Test
    void shouldCreateExponentialBackoffPolicy() {
        RetryPolicy policy = RetryPolicy.exponentialBackoff(5, Duration.ofMillis(100));
        
        assertEquals(5, policy.getMaxRetries());
        assertTrue(policy.isExponentialBackoff());
        assertEquals(2.0, policy.getMultiplier());
    }

    @Test
    void shouldCreateFixedDelayPolicy() {
        RetryPolicy policy = RetryPolicy.fixedDelay(3, Duration.ofSeconds(2));
        
        assertEquals(3, policy.getMaxRetries());
        assertEquals(Duration.ofSeconds(2), policy.getInitialDelay());
        assertFalse(policy.isExponentialBackoff());
    }

    @Test
    void shouldCalculateFixedDelay() {
        RetryPolicy policy = RetryPolicy.fixedDelay(3, Duration.ofSeconds(1));
        
        assertEquals(Duration.ofSeconds(1), policy.getDelayForAttempt(1));
        assertEquals(Duration.ofSeconds(1), policy.getDelayForAttempt(2));
        assertEquals(Duration.ofSeconds(1), policy.getDelayForAttempt(3));
    }

    @Test
    void shouldCalculateExponentialDelay() {
        RetryPolicy policy = RetryPolicy.exponentialBackoff(5, Duration.ofMillis(100));
        
        assertEquals(Duration.ofMillis(100), policy.getDelayForAttempt(1));
        assertEquals(Duration.ofMillis(200), policy.getDelayForAttempt(2));
        assertEquals(Duration.ofMillis(400), policy.getDelayForAttempt(3));
        assertEquals(Duration.ofMillis(800), policy.getDelayForAttempt(4));
    }

    @Test
    void shouldRespectMaxDelay() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(10)
                .initialDelay(Duration.ofSeconds(1))
                .maxDelay(Duration.ofSeconds(10))
                .exponentialBackoff(true)
                .multiplier(2.0)
                .build();
        
        // 1 * 2^9 = 512 seconds, but max is 10 seconds
        assertEquals(Duration.ofSeconds(10), policy.getDelayForAttempt(10));
    }

    @Test
    void shouldReturnZeroDelayForZeroAttempt() {
        RetryPolicy policy = RetryPolicy.defaultPolicy();
        
        assertEquals(Duration.ZERO, policy.getDelayForAttempt(0));
    }

    @Test
    void shouldRetryWhenAttemptsRemain() {
        RetryPolicy policy = RetryPolicy.builder().maxRetries(3).build();
        
        assertTrue(policy.shouldRetry(new RuntimeException(), 0));
        assertTrue(policy.shouldRetry(new RuntimeException(), 1));
        assertTrue(policy.shouldRetry(new RuntimeException(), 2));
        assertFalse(policy.shouldRetry(new RuntimeException(), 3));
    }

    @Test
    void shouldRetryOnlyForSpecificException() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(3)
                .retryOnException(IOException.class)
                .build();
        
        assertTrue(policy.shouldRetry(new IOException(), 0));
        assertFalse(policy.shouldRetry(new RuntimeException(), 0));
    }

    @Test
    void shouldRetryWithCustomPredicate() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(3)
                .retryOn(t -> t.getMessage() != null && t.getMessage().contains("retry"))
                .build();
        
        assertTrue(policy.shouldRetry(new RuntimeException("please retry"), 0));
        assertFalse(policy.shouldRetry(new RuntimeException("fatal error"), 0));
    }

    @Test
    void shouldBuildWithCustomMultiplier() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(5)
                .initialDelay(Duration.ofMillis(100))
                .exponentialBackoff(true)
                .multiplier(3.0)
                .build();
        
        assertEquals(Duration.ofMillis(100), policy.getDelayForAttempt(1));
        assertEquals(Duration.ofMillis(300), policy.getDelayForAttempt(2));
        assertEquals(Duration.ofMillis(900), policy.getDelayForAttempt(3));
    }
}
