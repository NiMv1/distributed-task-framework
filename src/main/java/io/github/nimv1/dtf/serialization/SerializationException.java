package io.github.nimv1.dtf.serialization;

/**
 * Exception thrown when serialization/deserialization fails.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
public class SerializationException extends RuntimeException {

    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
