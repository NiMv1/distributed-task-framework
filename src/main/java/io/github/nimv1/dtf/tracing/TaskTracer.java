package io.github.nimv1.dtf.tracing;

import io.github.nimv1.dtf.core.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributed tracing support for tasks.
 * 
 * <p>Integrates with SLF4J MDC for logging context propagation.</p>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class TaskTracer {

    private static final Logger log = LoggerFactory.getLogger(TaskTracer.class);

    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";
    public static final String PARENT_SPAN_ID = "parentSpanId";
    public static final String TASK_ID = "taskId";
    public static final String TASK_TYPE = "taskType";

    private final Map<String, TaskSpan> activeSpans = new ConcurrentHashMap<>();

    /**
     * Starts a new trace for a task.
     */
    public TaskSpan startTrace(Task task) {
        String traceId = generateId();
        String spanId = generateId();
        
        TaskSpan span = new TaskSpan(traceId, spanId, null, task.getId(), task.getType());
        activeSpans.put(spanId, span);
        
        setMdcContext(span);
        log.debug("Started trace {} for task {}", traceId, task.getId());
        
        return span;
    }

    /**
     * Starts a child span within an existing trace.
     */
    public TaskSpan startSpan(TaskSpan parent, Task task) {
        String spanId = generateId();
        
        TaskSpan span = new TaskSpan(parent.getTraceId(), spanId, parent.getSpanId(), 
                                      task.getId(), task.getType());
        activeSpans.put(spanId, span);
        
        setMdcContext(span);
        log.debug("Started span {} (parent: {}) for task {}", spanId, parent.getSpanId(), task.getId());
        
        return span;
    }

    /**
     * Ends a span.
     */
    public void endSpan(TaskSpan span) {
        span.end();
        activeSpans.remove(span.getSpanId());
        clearMdcContext();
        log.debug("Ended span {} (duration: {}ms)", span.getSpanId(), span.getDuration().toMillis());
    }

    /**
     * Gets an active span by ID.
     */
    public TaskSpan getSpan(String spanId) {
        return activeSpans.get(spanId);
    }

    private void setMdcContext(TaskSpan span) {
        MDC.put(TRACE_ID, span.getTraceId());
        MDC.put(SPAN_ID, span.getSpanId());
        if (span.getParentSpanId() != null) {
            MDC.put(PARENT_SPAN_ID, span.getParentSpanId());
        }
        MDC.put(TASK_ID, span.getTaskId());
        MDC.put(TASK_TYPE, span.getTaskType());
    }

    private void clearMdcContext() {
        MDC.remove(TRACE_ID);
        MDC.remove(SPAN_ID);
        MDC.remove(PARENT_SPAN_ID);
        MDC.remove(TASK_ID);
        MDC.remove(TASK_TYPE);
    }

    private String generateId() {
        return UUID.randomUUID().toString().substring(0, 16);
    }

    /**
     * Represents a span in a distributed trace.
     */
    public static class TaskSpan {
        private final String traceId;
        private final String spanId;
        private final String parentSpanId;
        private final String taskId;
        private final String taskType;
        private final Instant startTime;
        private Instant endTime;

        public TaskSpan(String traceId, String spanId, String parentSpanId, 
                        String taskId, String taskType) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
            this.taskId = taskId;
            this.taskType = taskType;
            this.startTime = Instant.now();
        }

        public void end() {
            this.endTime = Instant.now();
        }

        public Duration getDuration() {
            Instant end = endTime != null ? endTime : Instant.now();
            return Duration.between(startTime, end);
        }

        public String getTraceId() { return traceId; }
        public String getSpanId() { return spanId; }
        public String getParentSpanId() { return parentSpanId; }
        public String getTaskId() { return taskId; }
        public String getTaskType() { return taskType; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }

        @Override
        public String toString() {
            return "TaskSpan{traceId='" + traceId + "', spanId='" + spanId + 
                   "', taskId='" + taskId + "', duration=" + getDuration().toMillis() + "ms}";
        }
    }
}
