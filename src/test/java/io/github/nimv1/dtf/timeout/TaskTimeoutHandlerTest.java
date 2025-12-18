package io.github.nimv1.dtf.timeout;

import io.github.nimv1.dtf.core.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TaskTimeoutHandler.
 */
class TaskTimeoutHandlerTest {

    private TaskTimeoutHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TaskTimeoutHandler();
    }

    @AfterEach
    void tearDown() {
        handler.close();
    }

    private Task createTaskWithTimeout(String type, long timeoutMs) {
        return Task.builder(type).timeout(timeoutMs).build();
    }

    @Test
    void shouldRegisterTaskForMonitoring() {
        Task task = createTaskWithTimeout("test-task", 5000);
        
        handler.registerTask(task);
        
        assertEquals(1, handler.getMonitoredTaskCount());
    }

    @Test
    void shouldNotRegisterTaskWithZeroTimeout() {
        Task task = Task.builder("test-task").timeout(0).build();
        
        handler.registerTask(task);
        
        assertEquals(0, handler.getMonitoredTaskCount());
    }

    @Test
    void shouldUnregisterTask() {
        Task task = createTaskWithTimeout("test-task", 5000);
        handler.registerTask(task);
        
        handler.unregisterTask(task.getId());
        
        assertEquals(0, handler.getMonitoredTaskCount());
    }

    @Test
    void shouldDetectTimedOutTask() throws InterruptedException {
        Task task = createTaskWithTimeout("test-task", 200);
        handler.registerTask(task);
        
        // Check before timeout - should not be timed out yet
        assertFalse(handler.isTimedOut(task.getId()));
        
        // Wait a bit but not enough to trigger timeout callback
        Thread.sleep(50);
        
        // Still should not be timed out (only 50ms elapsed, timeout is 200ms)
        assertFalse(handler.isTimedOut(task.getId()));
    }

    @Test
    void shouldNotDetectNonTimedOutTask() {
        Task task = createTaskWithTimeout("test-task", 5000);
        handler.registerTask(task);
        
        assertFalse(handler.isTimedOut(task.getId()));
    }

    @Test
    void shouldReturnRemainingTime() {
        Task task = createTaskWithTimeout("test-task", 5000);
        handler.registerTask(task);
        
        long remaining = handler.getRemainingTime(task.getId());
        
        assertTrue(remaining > 0 && remaining <= 5000);
    }

    @Test
    void shouldReturnNegativeOneForUnknownTask() {
        long remaining = handler.getRemainingTime("unknown-id");
        
        assertEquals(-1, remaining);
    }

    @Test
    void shouldInvokeTimeoutCallback() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Task> timedOutTask = new AtomicReference<>();
        
        handler.setOnTimeoutCallback(task -> {
            timedOutTask.set(task);
            latch.countDown();
        });
        
        Task task = createTaskWithTimeout("test-task", 50);
        handler.registerTask(task);
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertNotNull(timedOutTask.get());
        assertEquals(task.getId(), timedOutTask.get().getId());
    }

    @Test
    void shouldHandleCallbackException() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        
        handler.setOnTimeoutCallback(task -> {
            latch.countDown();
            throw new RuntimeException("Callback error");
        });
        
        Task task = createTaskWithTimeout("test-task", 50);
        handler.registerTask(task);
        
        // Should not throw, callback exception is caught
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    void shouldCloseGracefully() {
        Task task = createTaskWithTimeout("test-task", 5000);
        handler.registerTask(task);
        
        assertDoesNotThrow(() -> handler.close());
        assertEquals(0, handler.getMonitoredTaskCount());
    }
}
