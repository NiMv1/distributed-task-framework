package io.github.nimv1.dtf.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.nimv1.dtf.core.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * JSON-based task serializer using Jackson.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class JsonTaskSerializer implements TaskSerializer {

    private static final Logger log = LoggerFactory.getLogger(JsonTaskSerializer.class);

    private final ObjectMapper objectMapper;

    public JsonTaskSerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public JsonTaskSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(Task task) {
        try {
            return objectMapper.writeValueAsBytes(task);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize task {}: {}", task.getId(), e.getMessage());
            throw new SerializationException("Failed to serialize task", e);
        }
    }

    @Override
    public Task deserialize(byte[] data) {
        try {
            return objectMapper.readValue(data, Task.class);
        } catch (Exception e) {
            log.error("Failed to deserialize task: {}", e.getMessage());
            throw new SerializationException("Failed to deserialize task", e);
        }
    }

    @Override
    public String serializeToString(Task task) {
        try {
            return objectMapper.writeValueAsString(task);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize task {}: {}", task.getId(), e.getMessage());
            throw new SerializationException("Failed to serialize task", e);
        }
    }

    @Override
    public Task deserializeFromString(String data) {
        try {
            return objectMapper.readValue(data, Task.class);
        } catch (Exception e) {
            log.error("Failed to deserialize task: {}", e.getMessage());
            throw new SerializationException("Failed to deserialize task", e);
        }
    }

    /**
     * Pretty prints a task as JSON.
     */
    public String toPrettyJson(Task task) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(task);
        } catch (JsonProcessingException e) {
            throw new SerializationException("Failed to serialize task", e);
        }
    }
}
