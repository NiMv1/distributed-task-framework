package io.github.nimv1.dtf.retry;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * Configurable retry policy for task execution.
 * Supports exponential backoff, fixed delay, and custom retry conditions.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class RetryPolicy {

    private final int maxRetries;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double multiplier;
    private final boolean exponentialBackoff;
    private final Predicate<Throwable> retryOn;

    private RetryPolicy(Builder builder) {
        this.maxRetries = builder.maxRetries;
        this.initialDelay = builder.initialDelay;
        this.maxDelay = builder.maxDelay;
        this.multiplier = builder.multiplier;
        this.exponentialBackoff = builder.exponentialBackoff;
        this.retryOn = builder.retryOn;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a default retry policy with 3 retries and 1 second delay.
     */
    public static RetryPolicy defaultPolicy() {
        return builder().build();
    }

    /**
     * Creates a policy with no retries.
     */
    public static RetryPolicy noRetry() {
        return builder().maxRetries(0).build();
    }

    /**
     * Creates a policy with exponential backoff.
     */
    public static RetryPolicy exponentialBackoff(int maxRetries, Duration initialDelay) {
        return builder()
                .maxRetries(maxRetries)
                .initialDelay(initialDelay)
                .exponentialBackoff(true)
                .multiplier(2.0)
                .build();
    }

    /**
     * Creates a policy with fixed delay.
     */
    public static RetryPolicy fixedDelay(int maxRetries, Duration delay) {
        return builder()
                .maxRetries(maxRetries)
                .initialDelay(delay)
                .exponentialBackoff(false)
                .build();
    }

    /**
     * Calculates the delay for the given retry attempt.
     */
    public Duration getDelayForAttempt(int attempt) {
        if (attempt <= 0) {
            return Duration.ZERO;
        }

        if (!exponentialBackoff) {
            return initialDelay;
        }

        long delayMs = (long) (initialDelay.toMillis() * Math.pow(multiplier, attempt - 1));
        long maxDelayMs = maxDelay.toMillis();
        return Duration.ofMillis(Math.min(delayMs, maxDelayMs));
    }

    /**
     * Checks if retry should be attempted for the given exception.
     */
    public boolean shouldRetry(Throwable throwable, int currentAttempt) {
        if (currentAttempt >= maxRetries) {
            return false;
        }
        return retryOn.test(throwable);
    }

    public int getMaxRetries() { return maxRetries; }
    public Duration getInitialDelay() { return initialDelay; }
    public Duration getMaxDelay() { return maxDelay; }
    public double getMultiplier() { return multiplier; }
    public boolean isExponentialBackoff() { return exponentialBackoff; }

    public static class Builder {
        private int maxRetries = 3;
        private Duration initialDelay = Duration.ofSeconds(1);
        private Duration maxDelay = Duration.ofMinutes(5);
        private double multiplier = 2.0;
        private boolean exponentialBackoff = false;
        private Predicate<Throwable> retryOn = t -> true;

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }

        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }

        public Builder multiplier(double multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        public Builder exponentialBackoff(boolean exponentialBackoff) {
            this.exponentialBackoff = exponentialBackoff;
            return this;
        }

        public Builder retryOn(Predicate<Throwable> retryOn) {
            this.retryOn = retryOn;
            return this;
        }

        public Builder retryOnException(Class<? extends Throwable> exceptionClass) {
            this.retryOn = t -> exceptionClass.isInstance(t);
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}
