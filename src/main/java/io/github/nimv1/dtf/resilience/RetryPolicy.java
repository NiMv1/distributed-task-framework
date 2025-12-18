package io.github.nimv1.dtf.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Configurable retry policy for task execution.
 * 
 * <p>Supports:</p>
 * <ul>
 *   <li>Fixed delay between retries</li>
 *   <li>Exponential backoff</li>
 *   <li>Custom retry predicates</li>
 *   <li>Max attempts limit</li>
 * </ul>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class RetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(RetryPolicy.class);

    private final int maxAttempts;
    private final Duration initialDelay;
    private final double multiplier;
    private final Duration maxDelay;
    private final Predicate<Exception> retryOn;

    private RetryPolicy(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.initialDelay = builder.initialDelay;
        this.multiplier = builder.multiplier;
        this.maxDelay = builder.maxDelay;
        this.retryOn = builder.retryOn;
    }

    /**
     * Creates a builder for RetryPolicy.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a simple retry policy with fixed delay.
     */
    public static RetryPolicy fixedDelay(int maxAttempts, Duration delay) {
        return builder()
                .maxAttempts(maxAttempts)
                .initialDelay(delay)
                .multiplier(1.0)
                .build();
    }

    /**
     * Creates a retry policy with exponential backoff.
     */
    public static RetryPolicy exponentialBackoff(int maxAttempts, Duration initialDelay) {
        return builder()
                .maxAttempts(maxAttempts)
                .initialDelay(initialDelay)
                .multiplier(2.0)
                .maxDelay(Duration.ofMinutes(1))
                .build();
    }

    /**
     * Executes the supplier with retry logic.
     *
     * @param supplier the operation to execute
     * @return the result
     * @throws Exception if all retries are exhausted
     */
    public <T> T execute(Supplier<T> supplier) throws Exception {
        Exception lastException = null;
        Duration currentDelay = initialDelay;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = supplier.get();
                if (attempt > 1) {
                    log.info("Retry succeeded on attempt {}", attempt);
                }
                return result;
            } catch (Exception e) {
                lastException = e;
                
                if (!shouldRetry(e) || attempt >= maxAttempts) {
                    log.warn("Retry exhausted after {} attempts", attempt);
                    throw e;
                }

                log.debug("Attempt {} failed, retrying in {}: {}", attempt, currentDelay, e.getMessage());
                
                try {
                    Thread.sleep(currentDelay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }

                currentDelay = calculateNextDelay(currentDelay);
            }
        }

        throw lastException != null ? lastException : new RuntimeException("Retry failed");
    }

    /**
     * Executes the runnable with retry logic.
     */
    public void execute(Runnable runnable) throws Exception {
        execute(() -> {
            runnable.run();
            return null;
        });
    }

    private boolean shouldRetry(Exception e) {
        return retryOn.test(e);
    }

    private Duration calculateNextDelay(Duration currentDelay) {
        long nextDelayMs = (long) (currentDelay.toMillis() * multiplier);
        Duration nextDelay = Duration.ofMillis(nextDelayMs);
        return nextDelay.compareTo(maxDelay) > 0 ? maxDelay : nextDelay;
    }

    public int getMaxAttempts() { return maxAttempts; }
    public Duration getInitialDelay() { return initialDelay; }
    public double getMultiplier() { return multiplier; }
    public Duration getMaxDelay() { return maxDelay; }

    public static class Builder {
        private int maxAttempts = 3;
        private Duration initialDelay = Duration.ofSeconds(1);
        private double multiplier = 1.0;
        private Duration maxDelay = Duration.ofMinutes(1);
        private Predicate<Exception> retryOn = e -> true;

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }

        public Builder multiplier(double multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }

        public Builder retryOn(Predicate<Exception> retryOn) {
            this.retryOn = retryOn;
            return this;
        }

        public Builder retryOn(Class<? extends Exception>... exceptionClasses) {
            this.retryOn = e -> {
                for (Class<? extends Exception> clazz : exceptionClasses) {
                    if (clazz.isInstance(e)) return true;
                }
                return false;
            };
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}
