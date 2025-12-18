package io.github.nimv1.dtf.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token bucket rate limiter for controlling task execution rate.
 * 
 * <p>Supports:</p>
 * <ul>
 *   <li>Configurable rate (tasks per second)</li>
 *   <li>Burst capacity</li>
 *   <li>Blocking and non-blocking acquire</li>
 * </ul>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final double permitsPerSecond;
    private final int burstCapacity;
    private final Semaphore semaphore;
    private final AtomicLong lastRefillTime;
    private final Object refillLock = new Object();

    /**
     * Creates a rate limiter with the specified rate.
     *
     * @param permitsPerSecond the number of permits per second
     */
    public RateLimiter(double permitsPerSecond) {
        this(permitsPerSecond, (int) Math.max(1, permitsPerSecond));
    }

    /**
     * Creates a rate limiter with the specified rate and burst capacity.
     *
     * @param permitsPerSecond the number of permits per second
     * @param burstCapacity the maximum burst capacity
     */
    public RateLimiter(double permitsPerSecond, int burstCapacity) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be positive");
        }
        if (burstCapacity <= 0) {
            throw new IllegalArgumentException("burstCapacity must be positive");
        }
        
        this.permitsPerSecond = permitsPerSecond;
        this.burstCapacity = burstCapacity;
        this.semaphore = new Semaphore(burstCapacity);
        this.lastRefillTime = new AtomicLong(System.nanoTime());
        
        log.debug("RateLimiter created: {} permits/sec, burst: {}", permitsPerSecond, burstCapacity);
    }

    /**
     * Acquires a permit, blocking until one is available.
     */
    public void acquire() {
        acquire(1);
    }

    /**
     * Acquires the specified number of permits, blocking until available.
     *
     * @param permits the number of permits to acquire
     */
    public void acquire(int permits) {
        refillTokens();
        try {
            semaphore.acquire(permits);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while acquiring rate limit permit", e);
        }
    }

    /**
     * Tries to acquire a permit without blocking.
     *
     * @return true if permit was acquired, false otherwise
     */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * Tries to acquire the specified number of permits without blocking.
     *
     * @param permits the number of permits to acquire
     * @return true if permits were acquired, false otherwise
     */
    public boolean tryAcquire(int permits) {
        refillTokens();
        return semaphore.tryAcquire(permits);
    }

    /**
     * Tries to acquire a permit within the specified timeout.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit
     * @return true if permit was acquired, false otherwise
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) {
        return tryAcquire(1, timeout, unit);
    }

    /**
     * Tries to acquire the specified number of permits within the timeout.
     *
     * @param permits the number of permits to acquire
     * @param timeout the maximum time to wait
     * @param unit the time unit
     * @return true if permits were acquired, false otherwise
     */
    public boolean tryAcquire(int permits, long timeout, TimeUnit unit) {
        refillTokens();
        try {
            return semaphore.tryAcquire(permits, timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void refillTokens() {
        synchronized (refillLock) {
            long now = System.nanoTime();
            long elapsed = now - lastRefillTime.get();
            
            double secondsElapsed = elapsed / 1_000_000_000.0;
            int newPermits = (int) (secondsElapsed * permitsPerSecond);
            
            if (newPermits > 0) {
                int currentPermits = semaphore.availablePermits();
                int permitsToAdd = Math.min(newPermits, burstCapacity - currentPermits);
                
                if (permitsToAdd > 0) {
                    semaphore.release(permitsToAdd);
                    lastRefillTime.set(now);
                }
            }
        }
    }

    /**
     * Returns the number of available permits.
     */
    public int availablePermits() {
        refillTokens();
        return semaphore.availablePermits();
    }

    /**
     * Returns the configured rate in permits per second.
     */
    public double getPermitsPerSecond() {
        return permitsPerSecond;
    }

    /**
     * Returns the burst capacity.
     */
    public int getBurstCapacity() {
        return burstCapacity;
    }
}
