package io.github.nimv1.dtf.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisTaskQueue.
 * 
 * @author NiMv1
 */
class RedisTaskQueueTest {

    private RedisTaskQueue queue;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ZSetOperations<String, String> zSetOps;
    private SetOperations<String, String> setOps;
    private ObjectMapper objectMapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        zSetOps = mock(ZSetOperations.class);
        setOps = mock(SetOperations.class);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        
        queue = new RedisTaskQueue(redisTemplate, "dtf:");
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldSubmitTask() throws Exception {
        Task task = Task.builder("test-task")
                .id("task-123")
                .payload(Map.of("key", "value"))
                .build();
        
        queue.submit(task);
        
        verify(valueOps).set(eq("dtf::tasks:task-123"), anyString());
        verify(zSetOps).add(eq("dtf::queue"), eq("task-123"), anyDouble());
    }

    @Test
    void shouldPollTask() throws Exception {
        Task task = Task.builder("test-task").id("task-456").build();
        // Use the same ObjectMapper configuration as RedisTaskQueue
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String taskJson = mapper.writeValueAsString(task);
        
        when(zSetOps.reverseRange("dtf::queue", 0, 0)).thenReturn(Set.of("task-456"));
        when(zSetOps.remove("dtf::queue", "task-456")).thenReturn(1L);
        when(valueOps.get("dtf::tasks:task-456")).thenReturn(taskJson);
        
        Optional<Task> polled = queue.poll();
        
        assertTrue(polled.isPresent());
        assertEquals("task-456", polled.get().getId());
        verify(setOps).add("dtf::processing", "task-456");
    }

    @Test
    void shouldReturnEmptyWhenQueueEmpty() {
        when(zSetOps.reverseRange("dtf::queue", 0, 0)).thenReturn(Set.of());
        
        Optional<Task> polled = queue.poll();
        
        assertFalse(polled.isPresent());
    }

    @Test
    void shouldReturnQueueSize() {
        when(zSetOps.size("dtf::queue")).thenReturn(5L);
        
        long size = queue.size();
        
        assertEquals(5, size);
    }

    @Test
    void shouldAcknowledgeTask() throws Exception {
        Task task = Task.builder("test-task").id("task-789").build();
        
        queue.acknowledge(task);
        
        verify(valueOps).set(eq("dtf::tasks:task-789"), anyString());
        verify(setOps).remove("dtf::processing", "task-789");
    }

    @Test
    void shouldCancelPendingTask() throws Exception {
        Task task = Task.builder("test-task").id("task-cancel").build();
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String taskJson = mapper.writeValueAsString(task);
        
        when(valueOps.get("dtf::tasks:task-cancel")).thenReturn(taskJson);
        when(zSetOps.remove("dtf::queue", "task-cancel")).thenReturn(1L);
        
        boolean cancelled = queue.cancel("task-cancel");
        
        assertTrue(cancelled);
    }
}
