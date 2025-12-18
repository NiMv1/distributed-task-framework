package io.github.nimv1.dtf.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Bulkhead pattern implementation for resource isolation.
 * 
 * <p>Limits the number of concurrent executions to prevent
 * resource exhaustion and cascading failures.</p>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class Bulkhead {

    private static final Logger log = LoggerFactory.getLogger(Bulkhead.class);

    private final String name;
    private final Semaphore semaphore;
    private final int maxConcurrentCalls;

    public Bulkhead(String name, int maxConcurrentCalls) {
        this.name = name;
        this.maxConcurrentCalls = maxConcurrentCalls;
        this.semaphore = new Semaphore(maxConcurrentCalls);
        log.debug("Bulkhead '{}' created with {} max concurrent calls", name, maxConcurrentCalls);
    }

    /**
     * Executes the supplier within the bulkhead.
     *
     * @param supplier the operation to execute
     * @return the result
     * @throws BulkheadFullException if bulkhead is full
     */
    public <T> T execute(Supplier<T> supplier) {
        if (!semaphore.tryAcquire()) {
            throw new BulkheadFullException("Bulkhead '" + name + "' is full");
        }
        try {
            return supplier.get();
        } finally {
            semaphore.release();
        }
    }

    /**
     * Executes the runnable within the bulkhead.
     */
    public void execute(Runnable runnable) {
        execute(() -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Tries to execute within timeout.
     *
     * @param supplier the operation
     * @param timeout the max wait time
     * @param unit the time unit
     * @return the result
     * @throws BulkheadFullException if timeout exceeded
     */
    public <T> T execute(Supplier<T> supplier, long timeout, TimeUnit unit) {
        try {
            if (!semaphore.tryAcquire(timeout, unit)) {
                throw new BulkheadFullException("Bulkhead '" + name + "' timeout after " + timeout + " " + unit);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BulkheadFullException("Bulkhead '" + name + "' interrupted", e);
        }
        try {
            return supplier.get();
        } finally {
            semaphore.release();
        }
    }

    public String getName() { return name; }
    public int getMaxConcurrentCalls() { return maxConcurrentCalls; }
    public int getAvailablePermits() { return semaphore.availablePermits(); }
    public int getActiveCount() { return maxConcurrentCalls - semaphore.availablePermits(); }

    @Override
    public String toString() {
        return "Bulkhead{name='" + name + "', active=" + getActiveCount() + "/" + maxConcurrentCalls + "}";
    }
}
