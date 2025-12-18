package io.github.nimv1.dtf.ratelimit;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.queue.TaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * A TaskQueue wrapper that applies rate limiting to task polling.
 * 
 * <p>Useful for controlling the rate at which tasks are processed,
 * preventing overload of downstream systems.</p>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class RateLimitedTaskQueue implements TaskQueue {

    private static final Logger log = LoggerFactory.getLogger(RateLimitedTaskQueue.class);

    private final TaskQueue delegate;
    private final RateLimiter rateLimiter;

    public RateLimitedTaskQueue(TaskQueue delegate, double permitsPerSecond) {
        this(delegate, new RateLimiter(permitsPerSecond));
    }

    public RateLimitedTaskQueue(TaskQueue delegate, RateLimiter rateLimiter) {
        this.delegate = delegate;
        this.rateLimiter = rateLimiter;
        log.info("RateLimitedTaskQueue created with {} permits/sec", rateLimiter.getPermitsPerSecond());
    }

    @Override
    public void submit(Task task) {
        delegate.submit(task);
    }

    @Override
    public Optional<Task> poll() {
        rateLimiter.acquire();
        return delegate.poll();
    }

    @Override
    public List<Task> pollBatch(int maxTasks) {
        rateLimiter.acquire(maxTasks);
        return delegate.pollBatch(maxTasks);
    }

    @Override
    public long size() {
        return delegate.size();
    }

    @Override
    public void acknowledge(Task task) {
        delegate.acknowledge(task);
    }

    @Override
    public void retry(Task task) {
        delegate.retry(task);
    }

    @Override
    public boolean cancel(String taskId) {
        return delegate.cancel(taskId);
    }

    @Override
    public Optional<Task> getById(String taskId) {
        return delegate.getById(taskId);
    }

    @Override
    public void deadLetter(Task task) {
        delegate.deadLetter(task);
    }

    /**
     * Tries to poll a task without blocking if rate limit is exceeded.
     *
     * @return the task if available and rate limit permits, empty otherwise
     */
    public Optional<Task> tryPoll() {
        if (rateLimiter.tryAcquire()) {
            return delegate.poll();
        }
        return Optional.empty();
    }

    /**
     * Tries to poll a task with timeout.
     *
     * @param timeout the maximum time to wait for rate limit
     * @param unit the time unit
     * @return the task if available, empty otherwise
     */
    public Optional<Task> tryPoll(long timeout, TimeUnit unit) {
        if (rateLimiter.tryAcquire(timeout, unit)) {
            return delegate.poll();
        }
        return Optional.empty();
    }

    /**
     * Returns the underlying rate limiter.
     */
    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    /**
     * Returns the delegate queue.
     */
    public TaskQueue getDelegate() {
        return delegate;
    }
}
