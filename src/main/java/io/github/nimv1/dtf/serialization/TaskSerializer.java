package io.github.nimv1.dtf.serialization;

import io.github.nimv1.dtf.core.Task;

/**
 * Interface for task serialization/deserialization.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public interface TaskSerializer {

    /**
     * Serializes a task to bytes.
     *
     * @param task the task to serialize
     * @return serialized bytes
     */
    byte[] serialize(Task task);

    /**
     * Deserializes bytes to a task.
     *
     * @param data the serialized data
     * @return the deserialized task
     */
    Task deserialize(byte[] data);

    /**
     * Serializes a task to string.
     *
     * @param task the task to serialize
     * @return serialized string
     */
    String serializeToString(Task task);

    /**
     * Deserializes string to a task.
     *
     * @param data the serialized string
     * @return the deserialized task
     */
    Task deserializeFromString(String data);
}
