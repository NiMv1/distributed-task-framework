package io.github.nimv1.dtf.annotation;

import io.github.nimv1.dtf.core.TaskPriority;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a method as a distributed task handler.
 * 
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Service
 * public class EmailService {
 *     
 *     @DistributedTask(type = "send-email", priority = TaskPriority.HIGH)
 *     public void sendEmail(String to, String subject, String body) {
 *         // This method will be executed asynchronously by workers
 *     }
 * }
 * }</pre>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedTask {

    /**
     * Task type identifier.
     */
    String type();

    /**
     * Task priority.
     */
    TaskPriority priority() default TaskPriority.NORMAL;

    /**
     * Maximum number of retries on failure.
     */
    int maxRetries() default 3;

    /**
     * Task timeout in milliseconds.
     */
    long timeout() default 300000;

    /**
     * Whether to execute asynchronously (submit to queue) or synchronously.
     */
    boolean async() default true;
}
