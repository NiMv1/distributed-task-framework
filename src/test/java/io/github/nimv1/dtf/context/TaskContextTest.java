package io.github.nimv1.dtf.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TaskContext.
 */
class TaskContextTest {

    @AfterEach
    void cleanup() {
        TaskContext.clear();
    }

    @Test
    void shouldCreateContext() {
        TaskContext context = new TaskContext("task-1");
        
        assertEquals("task-1", context.getTaskId());
        assertTrue(context.getAttributes().isEmpty());
        assertTrue(context.getHeaders().isEmpty());
    }

    @Test
    void shouldSetAndGetAttribute() {
        TaskContext context = new TaskContext("task-1");
        
        context.setAttribute("key", "value");
        
        Optional<String> result = context.getAttribute("key");
        assertTrue(result.isPresent());
        assertEquals("value", result.get());
    }

    @Test
    void shouldGetAttributeWithDefault() {
        TaskContext context = new TaskContext("task-1");
        
        String result = context.getAttribute("missing", "default");
        
        assertEquals("default", result);
    }

    @Test
    void shouldSetAndGetHeader() {
        TaskContext context = new TaskContext("task-1");
        
        context.setHeader("X-Trace-Id", "trace-123");
        
        Optional<String> result = context.getHeader("X-Trace-Id");
        assertTrue(result.isPresent());
        assertEquals("trace-123", result.get());
    }

    @Test
    void shouldSetCurrentContext() {
        TaskContext context = new TaskContext("task-1");
        
        TaskContext.setCurrent(context);
        
        Optional<TaskContext> current = TaskContext.current();
        assertTrue(current.isPresent());
        assertEquals("task-1", current.get().getTaskId());
    }

    @Test
    void shouldCreateChildContext() {
        TaskContext parent = new TaskContext("parent");
        parent.setHeader("X-Trace-Id", "trace-123");
        
        TaskContext child = parent.createChild("child");
        
        assertEquals("child", child.getTaskId());
        assertTrue(child.getParent().isPresent());
        assertEquals("parent", child.getParent().get().getTaskId());
        assertEquals("trace-123", child.getHeader("X-Trace-Id").orElse(null));
    }

    @Test
    void shouldRemoveAttribute() {
        TaskContext context = new TaskContext("task-1");
        context.setAttribute("key", "value");
        
        context.removeAttribute("key");
        
        assertFalse(context.getAttribute("key").isPresent());
    }
}
