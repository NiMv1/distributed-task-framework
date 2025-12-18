# Distributed Task Framework

[![Java](https://img.shields.io/badge/Java-17+-00FFFF?style=for-the-badge&logo=openjdk&logoColor=black)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2+-FF00FF?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache_2.0-00FF00?style=for-the-badge)](LICENSE)

> ⚡ **A lightweight, high-performance distributed task processing framework for Java**

**English** | [Русский](docs/README_RU.md)

## ✨ Features

- **Simple API** - Submit tasks with one line of code
- **Priority Queue** - Process critical tasks first
- **Automatic Retries** - Configurable retry policies with exponential backoff
- **Dead Letter Queue** - Failed tasks are preserved for analysis
- **Pluggable Backends** - In-Memory, Redis, Kafka support
- **Spring Boot Integration** - Auto-configuration out of the box
- **Metrics Ready** - Micrometer integration for monitoring

## 🚀 Quick Start

### 1. Add dependency

```xml
<dependency>
    <groupId>io.github.nimv1</groupId>
    <artifactId>distributed-task-framework</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Create a task handler

```java
@Component
public class EmailTaskHandler implements TaskHandler {
    
    @Override
    public String getTaskType() {
        return "send-email";
    }
    
    @Override
    public TaskResult handle(Task task) {
        String to = (String) task.getPayload().get("to");
        String subject = (String) task.getPayload().get("subject");
        
        // Send email logic here...
        emailService.send(to, subject);
        
        return TaskResult.success();
    }
}
```

### 3. Submit tasks

```java
@Service
public class NotificationService {
    
    @Autowired
    private TaskClient taskClient;
    
    public void sendWelcomeEmail(String userEmail) {
        taskClient.submit("send-email", Map.of(
            "to", userEmail,
            "subject", "Welcome!"
        ));
    }
}
```

## 📖 Documentation

### Task Configuration

```java
Task task = Task.builder("process-order")
    .id("order-12345")                    // Custom ID (optional)
    .payload(Map.of("orderId", 12345))    // Task data
    .priority(TaskPriority.HIGH)          // Priority level
    .maxRetries(5)                        // Retry attempts
    .timeout(60000)                       // Timeout in ms
    .build();

taskClient.submit(task);
```

### Priority Levels

| Priority | Value | Description |
|----------|-------|-------------|
| `CRITICAL` | 15 | Processed immediately |
| `HIGH` | 10 | Before normal tasks |
| `NORMAL` | 5 | Default priority |
| `LOW` | 0 | Processed last |

### Configuration

```yaml
dtf:
  enabled: true
  worker:
    concurrency: 4           # Number of worker threads
    poll-interval-ms: 100    # Queue polling interval
    auto-start: true         # Start workers automatically
  queue:
    type: in-memory          # Queue backend (in-memory, redis, kafka)
    redis-key-prefix: "dtf:" # Redis key prefix
```

### Task Lifecycle

```
┌──────────┐     ┌─────────┐     ┌───────────┐
│ PENDING  │────▶│ RUNNING │────▶│ COMPLETED │
└──────────┘     └─────────┘     └───────────┘
     ▲                │
     │                │ (on failure)
     │                ▼
     │          ┌─────────┐
     └──────────│  RETRY  │
   (if retries  └─────────┘
    remaining)        │
                      │ (max retries exceeded)
                      ▼
                ┌─────────┐
                │ FAILED  │──▶ Dead Letter Queue
                └─────────┘
```

## 🔧 Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Your Application                      │
├─────────────────────────────────────────────────────────┤
│  TaskClient                      TaskHandler(s)          │
│  ┌─────────┐                    ┌─────────────────┐     │
│  │ submit()│                    │ EmailHandler    │     │
│  │ cancel()│                    │ OrderHandler    │     │
│  │ status()│                    │ ReportHandler   │     │
│  └────┬────┘                    └────────┬────────┘     │
│       │                                  │              │
├───────┼──────────────────────────────────┼──────────────┤
│       ▼                                  ▼              │
│  ┌─────────────────────────────────────────────────┐   │
│  │                   TaskQueue                      │   │
│  │  (In-Memory / Redis / Kafka)                    │   │
│  └─────────────────────────────────────────────────┘   │
│       │                                  ▲              │
│       ▼                                  │              │
│  ┌─────────────────────────────────────────────────┐   │
│  │                    Worker(s)                     │   │
│  │  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐   │   │
│  │  │Thread 1│ │Thread 2│ │Thread 3│ │Thread 4│   │   │
│  │  └────────┘ └────────┘ └────────┘ └────────┘   │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

## 📝 Examples

### Async Order Processing

```java
@Component
public class OrderTaskHandler implements TaskHandler {
    
    @Autowired
    private OrderService orderService;
    
    @Override
    public String getTaskType() {
        return "process-order";
    }
    
    @Override
    public TaskResult handle(Task task) {
        Long orderId = (Long) task.getPayload().get("orderId");
        
        try {
            orderService.process(orderId);
            return TaskResult.success(Map.of("processed", true));
        } catch (Exception e) {
            return TaskResult.failure(e);
        }
    }
}
```

### Scheduled Reports

```java
@Service
public class ReportScheduler {
    
    @Autowired
    private TaskClient taskClient;
    
    @Scheduled(cron = "0 0 6 * * *") // Every day at 6 AM
    public void scheduleDailyReport() {
        taskClient.submit("generate-report", Map.of(
            "type", "daily",
            "date", LocalDate.now().toString()
        ), TaskPriority.LOW);
    }
}
```

## 🗺️ Roadmap

- [x] Core task processing engine
- [x] In-memory queue implementation
- [x] Priority-based scheduling
- [x] Automatic retries
- [x] Redis queue backend
- [x] Kafka queue backend
- [x] Metrics & monitoring (Micrometer)
- [x] Web dashboard
- [x] Delayed/scheduled tasks
- [x] Task dependencies (DAG)
- [x] Rate limiting
- [x] Resilience (Circuit Breaker, Retry, Bulkhead, Timeout)
- [x] Event-driven architecture (Task Events)
- [x] Batch processing (TaskBatch, BatchProcessor)
- [x] Worker Pool
- [x] Task Interceptors (preHandle, postHandle, afterCompletion)
- [x] Task Context (thread-local, headers, parent-child)
- [x] Task Serialization (JSON)
- [x] Health Indicators (Queue, WorkerPool)
- [x] Priority Queue (CRITICAL > HIGH > NORMAL > LOW)
- [x] Cron Scheduler (fixedRate, fixedDelay, once)
- [x] Distributed Tracing (TaskTracer, MDC integration)
- [x] Async Callbacks (TaskCallback, CallbackRegistry)
- [x] Metrics Collector (TaskMetricsCollector)
- [x] Dead Letter Queue Handler (DeadLetterQueueHandler)
- [x] Task Timeout Handler (TaskTimeoutHandler)
- [x] Retry Policy (exponential backoff, fixed delay, custom predicates)

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 👤 Author

**NiMv1** - [GitHub](https://github.com/NiMv1) | [Portfolio](https://nimv1.github.io)
