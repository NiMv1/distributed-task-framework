# Distributed Task Framework

[![Java CI](https://github.com/NiMv1/distributed-task-framework/actions/workflows/maven.yml/badge.svg)](https://github.com/NiMv1/distributed-task-framework/actions/workflows/maven.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A lightweight, production-ready framework for distributed task processing in Java.

## Features

### Core
- **Task Queue** - In-memory and Redis-backed task queues
- **Priority Queue** - Task prioritization with multiple priority levels
- **Task Handlers** - Pluggable task processing handlers
- **Task Results** - Structured result handling with success/failure states

### Resilience
- **Circuit Breaker** - Automatic failure detection and recovery
- **Retry Policy** - Configurable retry with exponential backoff
- **Rate Limiter** - Token bucket and sliding window algorithms
- **Timeout Handler** - Task timeout detection and callbacks

### Batch Processing
- **Batch Processor** - Parallel batch task execution
- **Task Partitioner** - Split large tasks into subtasks
- **Task Aggregator** - Combine partition results

### Observability
- **Metrics Collector** - Task execution metrics
- **Dead Letter Queue** - Failed task handling

## Quick Start

### Maven
```xml
<dependency>
    <groupId>io.github.nimv1</groupId>
    <artifactId>distributed-task-framework</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Basic Usage

```java
// Create a task queue
TaskQueue queue = new InMemoryTaskQueue();

// Create and submit a task
Task task = Task.builder("email-send")
    .payload(Map.of("to", "user@example.com", "subject", "Hello"))
    .priority(TaskPriority.HIGH)
    .build();

queue.enqueue(task);

// Process tasks
Task polled = queue.poll();
// ... process task
polled.markCompleted();
```

### Task Partitioning

```java
// Split large task into smaller partitions
TaskPartitioner partitioner = new TaskPartitioner();

Map<String, Object> payload = Map.of("items", List.of(1, 2, 3, ..., 1000));
Task task = Task.builder("process-items")
    .payload(payload)
    .build();

// Partition by size (100 items per partition)
List<Task> subtasks = partitioner.partitionByList(task, "items", 100);

// Or partition a numeric range
List<Task> rangeSubtasks = partitioner.partitionRange(task, 0, 1000000, 10000);
```

### Result Aggregation

```java
TaskAggregator aggregator = new TaskAggregator();

// Register aggregation context
aggregator.register("parent-task-id", 10); // 10 partitions

// Add results as partitions complete
aggregator.addResult("parent-task-id", 0, TaskResult.success(data0));
aggregator.addResult("parent-task-id", 1, TaskResult.success(data1));
// ...

// Aggregate results
List<TaskResult> allResults = aggregator.collectResults("parent-task-id");
long totalProcessed = aggregator.sumNumericData("parent-task-id", "processed");
List<String> combinedItems = aggregator.collectListData("parent-task-id", "items");
```

### Circuit Breaker

```java
CircuitBreaker breaker = CircuitBreaker.builder("external-api")
    .failureThreshold(5)
    .resetTimeout(Duration.ofSeconds(30))
    .build();

TaskResult result = breaker.execute(() -> {
    // Call external service
    return callExternalApi();
});
```

### Retry Policy

```java
RetryPolicy policy = RetryPolicy.builder()
    .maxAttempts(3)
    .exponentialBackoff(Duration.ofMillis(100), 2.0)
    .maxDelay(Duration.ofSeconds(10))
    .retryOn(IOException.class)
    .build();

TaskResult result = policy.execute(() -> {
    // Potentially failing operation
    return riskyOperation();
});
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Task Submission                          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Task Partitioner                         │
│  (Split large tasks into manageable subtasks)               │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Priority Queue                           │
│  (InMemory / Redis-backed)                                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Task Workers                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │   Worker 1  │  │   Worker 2  │  │   Worker N  │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ Circuit Breaker │  │  Rate Limiter   │  │ Timeout Handler │
└─────────────────┘  └─────────────────┘  └─────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Task Aggregator                          │
│  (Combine results from partitioned tasks)                   │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
┌─────────────────────────┐      ┌─────────────────────────┐
│    Metrics Collector    │      │    Dead Letter Queue    │
└─────────────────────────┘      └─────────────────────────┘
```

## Testing

```bash
mvn test
```

**123 tests** covering all components.

## Requirements

- Java 17+
- Maven 3.8+

## License

MIT License - see [LICENSE](LICENSE) for details.

## Author

**NiMv1** - [GitHub](https://github.com/NiMv1)