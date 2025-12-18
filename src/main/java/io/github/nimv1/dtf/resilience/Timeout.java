package io.github.nimv1.dtf.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Timeout wrapper for task execution.
 * 
 * <p>Cancels operations that exceed the configured timeout.</p>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class Timeout {

    private static final Logger log = LoggerFactory.getLogger(Timeout.class);

    private final String name;
    private final Duration timeout;
    private final ExecutorService executor;

    public Timeout(String name, Duration timeout) {
        this(name, timeout, Executors.newCachedThreadPool());
    }

    public Timeout(String name, Duration timeout, ExecutorService executor) {
        this.name = name;
        this.timeout = timeout;
        this.executor = executor;
        log.debug("Timeout '{}' created with duration: {}", name, timeout);
    }

    /**
     * Executes the supplier with timeout.
     *
     * @param supplier the operation to execute
     * @return the result
     * @throws TimeoutException if operation exceeds timeout
     */
    public <T> T execute(Supplier<T> supplier) throws TimeoutException {
        Future<T> future = executor.submit(supplier::get);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException("Timeout '" + name + "' exceeded after " + timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new TimeoutException("Timeout '" + name + "' interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Execution failed in timeout '" + name + "'", e.getCause());
        }
    }

    /**
     * Executes the runnable with timeout.
     */
    public void execute(Runnable runnable) throws TimeoutException {
        execute(() -> {
            runnable.run();
            return null;
        });
    }

    public String getName() { return name; }
    public Duration getTimeout() { return timeout; }

    /**
     * Shuts down the executor.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
