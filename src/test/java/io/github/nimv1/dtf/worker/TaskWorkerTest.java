package io.github.nimv1.dtf.worker;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskResult;
import io.github.nimv1.dtf.handler.TaskHandler;
import io.github.nimv1.dtf.queue.InMemoryTaskQueue;
import io.github.nimv1.dtf.queue.TaskQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TaskWorker.
 */
class TaskWorkerTest {

    private TaskQueue taskQueue;
    private TaskWorker worker;

    @BeforeEach
    void setUp() {
        taskQueue = new InMemoryTaskQueue();
    }

    @AfterEach
    void tearDown() {
        if (worker != null && worker.isRunning()) {
            worker.stop();
        }
    }

    @Test
    void shouldStartAndStop() {
        worker = new TaskWorker("test-worker", taskQueue);
        
        assertFalse(worker.isRunning());
        
        worker.start();
        assertTrue(worker.isRunning());
        
        worker.stop();
        assertFalse(worker.isRunning());
    }

    @Test
    void shouldProcessTask() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger processedValue = new AtomicInteger(0);

        TaskHandler handler = new TaskHandler() {
            @Override
            public String getTaskType() { return "compute"; }
            
            @Override
            public TaskResult handle(Task task) {
                processedValue.set((int) task.getPayload().get("value"));
                latch.countDown();
                return TaskResult.success();
            }
        };

        worker = new TaskWorker("test-worker", taskQueue, handler);
        worker.start();

        Task task = Task.builder("compute")
                .payload(Map.of("value", 42))
                .build();
        taskQueue.submit(task);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(42, processedValue.get());
        
        await().atMost(1, TimeUnit.SECONDS)
                .until(() -> worker.getProcessedCount() == 1);
    }

    @Test
    void shouldProcessMultipleTasks() throws InterruptedException {
        int taskCount = 10;
        CountDownLatch latch = new CountDownLatch(taskCount);
        List<Integer> processed = Collections.synchronizedList(new ArrayList<>());

        TaskHandler handler = new TaskHandler() {
            @Override
            public String getTaskType() { return "process"; }
            
            @Override
            public TaskResult handle(Task task) {
                processed.add((int) task.getPayload().get("id"));
                latch.countDown();
                return TaskResult.success();
            }
        };

        worker = new TaskWorker("test-worker", taskQueue, handler);
        worker.start();

        for (int i = 0; i < taskCount; i++) {
            Task task = Task.builder("process")
                    .payload(Map.of("id", i))
                    .build();
            taskQueue.submit(task);
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(taskCount, processed.size());
        
        await().atMost(1, TimeUnit.SECONDS)
                .until(() -> worker.getProcessedCount() == taskCount);
    }

    @Test
    void shouldHandleTaskFailure() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        TaskHandler handler = new TaskHandler() {
            @Override
            public String getTaskType() { return "failing"; }
            
            @Override
            public TaskResult handle(Task task) {
                latch.countDown();
                return TaskResult.failure("Intentional failure");
            }
        };

        worker = new TaskWorker("test-worker", taskQueue, handler);
        worker.start();

        Task task = Task.builder("failing")
                .payload(new HashMap<>())
                .maxRetries(0)
                .build();
        taskQueue.submit(task);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        
        await().atMost(1, TimeUnit.SECONDS)
                .until(() -> worker.getFailedCount() == 1);
    }

    @Test
    void shouldMoveUnhandledTaskToDLQ() throws InterruptedException {
        worker = new TaskWorker("test-worker", taskQueue);
        worker.start();

        Task task = Task.builder("unknown-type")
                .payload(new HashMap<>())
                .build();
        taskQueue.submit(task);

        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> worker.getFailedCount() == 1);
    }

    @Test
    void shouldCallCompletionCallback() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> completedIds = Collections.synchronizedList(new ArrayList<>());

        TaskHandler handler = new TaskHandler() {
            @Override
            public String getTaskType() { return "callback-test"; }
            
            @Override
            public TaskResult handle(Task task) {
                return TaskResult.success();
            }
        };

        worker = new TaskWorker("test-worker", taskQueue, handler);
        worker.onTaskComplete(task -> {
            completedIds.add(task.getId());
            latch.countDown();
        });
        worker.start();

        Task task = Task.builder("callback-test")
                .id("task-123")
                .payload(new HashMap<>())
                .build();
        taskQueue.submit(task);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(completedIds.contains("task-123"));
    }

    @Test
    void shouldCallFailureCallback() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> failedIds = Collections.synchronizedList(new ArrayList<>());

        TaskHandler handler = new TaskHandler() {
            @Override
            public String getTaskType() { return "fail-test"; }
            
            @Override
            public TaskResult handle(Task task) {
                throw new RuntimeException("Test exception");
            }
        };

        worker = new TaskWorker("test-worker", taskQueue, handler);
        worker.onTaskFailed(task -> {
            failedIds.add(task.getId());
            latch.countDown();
        });
        worker.start();

        Task task = Task.builder("fail-test")
                .id("task-456")
                .payload(new HashMap<>())
                .maxRetries(0)
                .build();
        taskQueue.submit(task);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(failedIds.contains("task-456"));
    }

    @Test
    void shouldRegisterMultipleHandlers() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        List<String> processed = Collections.synchronizedList(new ArrayList<>());

        worker = new TaskWorker("test-worker", taskQueue);
        
        worker.registerHandler(new TaskHandler() {
            @Override
            public String getTaskType() { return "type-a"; }
            
            @Override
            public TaskResult handle(Task task) {
                processed.add("A");
                latch.countDown();
                return TaskResult.success();
            }
        });
        
        worker.registerHandler(new TaskHandler() {
            @Override
            public String getTaskType() { return "type-b"; }
            
            @Override
            public TaskResult handle(Task task) {
                processed.add("B");
                latch.countDown();
                return TaskResult.success();
            }
        });

        worker.start();

        taskQueue.submit(Task.builder("type-a").payload(new HashMap<>()).build());
        taskQueue.submit(Task.builder("type-b").payload(new HashMap<>()).build());

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(processed.contains("A"));
        assertTrue(processed.contains("B"));
    }

    @Test
    void shouldReturnWorkerStats() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);

        TaskHandler handler = new TaskHandler() {
            @Override
            public String getTaskType() { return "stats-test"; }
            
            @Override
            public TaskResult handle(Task task) {
                latch.countDown();
                return TaskResult.success();
            }
        };

        worker = new TaskWorker("stats-worker", taskQueue, handler);
        worker.start();

        for (int i = 0; i < 3; i++) {
            taskQueue.submit(Task.builder("stats-test").payload(new HashMap<>()).build());
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        
        await().atMost(1, TimeUnit.SECONDS)
                .until(() -> worker.getProcessedCount() == 3);

        TaskWorker.WorkerStats stats = worker.getStats();
        assertEquals("stats-worker", stats.workerId());
        assertTrue(stats.running());
        assertEquals(3, stats.processedCount());
        assertEquals(0, stats.failedCount());
        assertEquals(3, stats.totalCount());
        assertEquals(1.0, stats.successRate(), 0.001);
    }

    @Test
    void shouldNotStartTwice() {
        worker = new TaskWorker("test-worker", taskQueue);
        
        worker.start();
        assertTrue(worker.isRunning());
        
        worker.start(); // Should be no-op
        assertTrue(worker.isRunning());
        
        worker.stop();
        assertFalse(worker.isRunning());
    }

    @Test
    void shouldUseCustomPollInterval() {
        worker = new TaskWorker("test-worker", taskQueue, 500);
        assertEquals("test-worker", worker.getWorkerId());
    }
}
