package io.github.nimv1.dtf.demo;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskResult;
import io.github.nimv1.dtf.partition.TaskAggregator;
import io.github.nimv1.dtf.partition.TaskPartitioner;
import io.github.nimv1.dtf.queue.InMemoryTaskQueue;
import io.github.nimv1.dtf.queue.TaskQueue;
import io.github.nimv1.dtf.scheduler.TaskScheduler;
import io.github.nimv1.dtf.batch.BatchProcessor;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Demo application showcasing distributed-task-framework features.
 * Run this to see the framework in action!
 */
public class DemoApplication {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║     Distributed Task Framework - Demo Application          ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Demo 1: Basic Task Processing
        demo1_BasicTaskProcessing();
        
        // Demo 2: Task Partitioning & Aggregation
        demo2_TaskPartitioning();
        
        // Demo 3: Scheduled Tasks
        demo3_ScheduledTasks();
        
        // Demo 4: Worker Pool
        demo4_WorkerPool();

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                    Demo Complete!                          ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
    }

    /**
     * Demo 1: Basic task queue operations
     */
    private static void demo1_BasicTaskProcessing() {
        System.out.println("┌────────────────────────────────────────────────────────────┐");
        System.out.println("│ Demo 1: Basic Task Processing                              │");
        System.out.println("└────────────────────────────────────────────────────────────┘");

        TaskQueue queue = new InMemoryTaskQueue();

        // Create and submit tasks
        for (int i = 1; i <= 5; i++) {
            Task task = Task.builder("email-send")
                    .payload(Map.of(
                            "to", "user" + i + "@example.com",
                            "subject", "Welcome!",
                            "body", "Hello from task " + i
                    ))
                    .build();
            queue.submit(task);
            System.out.println("  ✓ Submitted task: " + task.getId().substring(0, 8) + "...");
        }

        System.out.println("  Queue size: " + queue.size());

        // Process tasks
        System.out.println("  Processing tasks...");
        while (queue.size() > 0) {
            queue.poll().ifPresent(task -> {
                String to = (String) task.getPayload().get("to");
                System.out.println("    → Processed email to: " + to);
                queue.acknowledge(task);
            });
        }

        System.out.println("  ✓ All tasks processed!");
        System.out.println();
    }

    /**
     * Demo 2: Task partitioning for parallel processing
     */
    private static void demo2_TaskPartitioning() {
        System.out.println("┌────────────────────────────────────────────────────────────┐");
        System.out.println("│ Demo 2: Task Partitioning & Aggregation                    │");
        System.out.println("└────────────────────────────────────────────────────────────┘");

        TaskPartitioner partitioner = new TaskPartitioner();
        TaskAggregator aggregator = new TaskAggregator();

        // Create a large task with 100 items
        List<Integer> items = IntStream.rangeClosed(1, 100).boxed().toList();
        Task bigTask = Task.builder("process-numbers")
                .id("big-task-001")
                .payload(Map.of("numbers", new ArrayList<>(items)))
                .build();

        System.out.println("  Original task: " + items.size() + " items");

        // Partition into smaller tasks (25 items each)
        List<Task> partitions = partitioner.partitionByList(bigTask, "numbers", 25);
        System.out.println("  Partitioned into: " + partitions.size() + " subtasks");

        // Register aggregation
        aggregator.register("big-task-001", partitions.size());

        // Process each partition and aggregate results
        for (int i = 0; i < partitions.size(); i++) {
            Task partition = partitions.get(i);
            @SuppressWarnings("unchecked")
            List<Integer> nums = (List<Integer>) partition.getPayload().get("numbers");
            
            // Calculate sum for this partition
            int sum = nums.stream().mapToInt(Integer::intValue).sum();
            
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("partitionSum", sum);
            resultData.put("count", nums.size());
            
            aggregator.addResult("big-task-001", i, TaskResult.success(resultData));
            System.out.println("    Partition " + i + ": sum=" + sum + ", count=" + nums.size());
        }

        // Aggregate results
        long totalSum = aggregator.sumNumericData("big-task-001", "partitionSum");
        long totalCount = aggregator.sumNumericData("big-task-001", "count");
        
        System.out.println("  ✓ Aggregated results:");
        System.out.println("    Total sum: " + totalSum + " (expected: 5050)");
        System.out.println("    Total count: " + totalCount);
        System.out.println("    All succeeded: " + aggregator.allSucceeded("big-task-001"));
        
        aggregator.cleanup("big-task-001");
        System.out.println();
    }

    /**
     * Demo 3: Scheduled and delayed tasks
     */
    private static void demo3_ScheduledTasks() throws InterruptedException {
        System.out.println("┌────────────────────────────────────────────────────────────┐");
        System.out.println("│ Demo 3: Scheduled Tasks                                    │");
        System.out.println("└────────────────────────────────────────────────────────────┘");

        TaskQueue queue = new InMemoryTaskQueue();
        TaskScheduler scheduler = new TaskScheduler(queue);

        // Schedule a delayed task
        System.out.println("  Scheduling task to run in 500ms...");
        long start = System.currentTimeMillis();
        
        scheduler.schedule("delayed-task", 
                Map.of("message", "Hello from the future!"), 
                Duration.ofMillis(500));

        // Wait for task to appear
        while (queue.size() == 0 && System.currentTimeMillis() - start < 1000) {
            Thread.sleep(50);
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  ✓ Task appeared after " + elapsed + "ms");

        queue.poll().ifPresent(task -> {
            System.out.println("    Message: " + task.getPayload().get("message"));
        });

        // Schedule recurring task
        System.out.println("  Scheduling recurring task (every 200ms)...");
        String scheduleId = scheduler.scheduleAtFixedRate("heartbeat",
                Map.of("type", "ping"),
                Duration.ofMillis(100),
                Duration.ofMillis(200));

        // Let it run a few times
        Thread.sleep(700);
        
        int count = 0;
        while (queue.poll().isPresent()) {
            count++;
        }
        System.out.println("  ✓ Received " + count + " heartbeats");

        // Cancel recurring task
        scheduler.cancel(scheduleId);
        System.out.println("  ✓ Recurring task cancelled");

        scheduler.shutdown();
        System.out.println();
    }

    /**
     * Demo 4: Batch Processing
     */
    private static void demo4_WorkerPool() throws InterruptedException {
        System.out.println("┌────────────────────────────────────────────────────────────┐");
        System.out.println("│ Demo 4: Batch Processing                                   │");
        System.out.println("└────────────────────────────────────────────────────────────┘");

        TaskQueue queue = new InMemoryTaskQueue();
        BatchProcessor processor = new BatchProcessor(queue, 4);

        // Register handler
        processor.registerHandler("compute", new io.github.nimv1.dtf.handler.TaskHandler() {
            @Override
            public String getTaskType() { return "compute"; }
            
            @Override
            public TaskResult handle(Task task) {
                int value = (int) task.getPayload().get("value");
                int result = value * 2;
                try {
                    Thread.sleep(50); // Simulate work
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return TaskResult.success(Map.of("result", result));
            }
        });

        // Create batch of tasks
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Task task = Task.builder("compute")
                    .payload(Map.of("value", i * 10))
                    .build();
            tasks.add(task);
        }

        System.out.println("  Submitting batch of " + tasks.size() + " tasks...");
        
        // Process batch
        var future = processor.submitBatch(tasks);
        
        // Wait for completion
        var batch = future.join();
        
        System.out.println("  ✓ Batch completed!");
        System.out.println("    Total: " + batch.getTotalCount());
        System.out.println("    Completed: " + batch.getCompletedCount());
        System.out.println("    Failed: " + batch.getFailedCount());
        System.out.println("    Progress: " + String.format("%.0f%%", batch.getProgress()));

        processor.shutdown();
        System.out.println("  ✓ Processor shutdown");
        System.out.println();
    }
}
