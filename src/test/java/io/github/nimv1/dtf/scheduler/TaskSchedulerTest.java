package io.github.nimv1.dtf.scheduler;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.queue.InMemoryTaskQueue;
import io.github.nimv1.dtf.queue.TaskQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TaskScheduler.
 */
class TaskSchedulerTest {

    private TaskQueue taskQueue;
    private TaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        taskQueue = new InMemoryTaskQueue();
        scheduler = new TaskScheduler(taskQueue);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    @Test
    void shouldScheduleDelayedTask() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");

        String taskId = scheduler.schedule("test-task", payload, Duration.ofMillis(100));

        assertNotNull(taskId);
        
        // Task should not be in queue immediately
        assertTrue(taskQueue.poll().isEmpty());

        // Wait for task to be submitted
        await().atMost(500, TimeUnit.MILLISECONDS)
                .until(() -> taskQueue.poll().isPresent());
    }

    @Test
    void shouldScheduleTaskAtSpecificTime() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("scheduled", true);

        Instant scheduledTime = Instant.now().plusMillis(100);
        String taskId = scheduler.scheduleAt("test-task", payload, scheduledTime);

        assertNotNull(taskId);

        await().atMost(500, TimeUnit.MILLISECONDS)
                .until(() -> taskQueue.poll().isPresent());
    }

    @Test
    void shouldScheduleImmediatelyForPastTime() {
        Map<String, Object> payload = new HashMap<>();
        
        // Schedule for past time
        Instant pastTime = Instant.now().minusSeconds(10);
        String taskId = scheduler.scheduleAt("test-task", payload, pastTime);

        assertNotNull(taskId);

        // Should be submitted almost immediately
        await().atMost(200, TimeUnit.MILLISECONDS)
                .until(() -> taskQueue.poll().isPresent());
    }

    @Test
    void shouldScheduleRecurringTaskAtFixedRate() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("recurring", true);

        String scheduleId = scheduler.scheduleAtFixedRate(
                "recurring-task", payload, 
                Duration.ofMillis(50), Duration.ofMillis(100));

        assertNotNull(scheduleId);
        assertTrue(scheduleId.startsWith("schedule-"));

        // Wait for at least 2 executions
        await().atMost(1, TimeUnit.SECONDS)
                .until(() -> taskQueue.size() >= 2);
    }

    @Test
    void shouldScheduleRecurringTaskWithFixedDelay() {
        Map<String, Object> payload = new HashMap<>();

        String scheduleId = scheduler.scheduleWithFixedDelay(
                "delayed-task", payload,
                Duration.ofMillis(50), Duration.ofMillis(100));

        assertNotNull(scheduleId);

        await().atMost(1, TimeUnit.SECONDS)
                .until(() -> taskQueue.size() >= 2);
    }

    @Test
    void shouldCancelScheduledTask() {
        Map<String, Object> payload = new HashMap<>();

        String taskId = scheduler.schedule("test-task", payload, Duration.ofSeconds(10));

        boolean cancelled = scheduler.cancel(taskId);

        assertTrue(cancelled);
        
        // Wait a bit and verify task was not submitted
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(taskQueue.poll().isEmpty());
    }

    @Test
    void shouldCancelRecurringTask() {
        Map<String, Object> payload = new HashMap<>();

        String scheduleId = scheduler.scheduleAtFixedRate(
                "recurring-task", payload,
                Duration.ofSeconds(10), Duration.ofSeconds(10));

        boolean cancelled = scheduler.cancel(scheduleId);

        assertTrue(cancelled);
    }

    @Test
    void shouldReturnFalseWhenCancellingNonExistentTask() {
        boolean cancelled = scheduler.cancel("non-existent-id");
        assertFalse(cancelled);
    }

    @Test
    void shouldTrackActiveScheduleCount() {
        assertEquals(0, scheduler.getActiveScheduleCount());

        scheduler.schedule("task1", new HashMap<>(), Duration.ofSeconds(10));
        scheduler.schedule("task2", new HashMap<>(), Duration.ofSeconds(10));
        scheduler.scheduleAtFixedRate("task3", new HashMap<>(), 
                Duration.ofSeconds(10), Duration.ofSeconds(10));

        assertEquals(3, scheduler.getActiveScheduleCount());
    }

    @Test
    void shouldDecreaseActiveCountAfterCancel() {
        String id1 = scheduler.schedule("task1", new HashMap<>(), Duration.ofSeconds(10));
        String id2 = scheduler.schedule("task2", new HashMap<>(), Duration.ofSeconds(10));

        assertEquals(2, scheduler.getActiveScheduleCount());

        scheduler.cancel(id1);

        assertEquals(1, scheduler.getActiveScheduleCount());
    }

    @Test
    void shouldPreservePayloadInScheduledTask() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", 123);
        payload.put("action", "notify");

        scheduler.schedule("notification", payload, Duration.ofMillis(50));

        await().atMost(200, TimeUnit.MILLISECONDS)
                .until(() -> taskQueue.poll().isPresent());

        // Re-poll since we consumed it in the await
        // Let's schedule another one to verify payload
        scheduler.schedule("notification", payload, Duration.ofMillis(50));
        
        await().atMost(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var optTask = taskQueue.poll();
                    assertTrue(optTask.isPresent());
                    Task task = optTask.get();
                    assertEquals("notification", task.getType());
                    assertEquals(123, task.getPayload().get("userId"));
                    assertEquals("notify", task.getPayload().get("action"));
                });
    }

    @Test
    void shouldShutdownGracefully() {
        scheduler.schedule("task", new HashMap<>(), Duration.ofSeconds(10));
        
        // Should not throw
        assertDoesNotThrow(() -> scheduler.shutdown());
    }

    @Test
    void shouldCreateSchedulerWithCustomPoolSize() {
        TaskScheduler customScheduler = new TaskScheduler(taskQueue, 4);
        
        assertNotNull(customScheduler);
        
        customScheduler.shutdown();
    }
}
