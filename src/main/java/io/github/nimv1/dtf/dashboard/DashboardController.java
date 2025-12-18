package io.github.nimv1.dtf.dashboard;

import io.github.nimv1.dtf.core.Task;
import io.github.nimv1.dtf.core.TaskStatus;
import io.github.nimv1.dtf.queue.TaskQueue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for the Task Dashboard API.
 * 
 * <p>Provides endpoints for monitoring and managing tasks:</p>
 * <ul>
 *   <li>GET /dtf/api/stats - Queue statistics</li>
 *   <li>GET /dtf/api/tasks/{id} - Get task by ID</li>
 *   <li>DELETE /dtf/api/tasks/{id} - Cancel task</li>
 * </ul>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
@RestController
@RequestMapping("/dtf/api")
@ConditionalOnProperty(prefix = "dtf.dashboard", name = "enabled", havingValue = "true")
public class DashboardController {

    private final TaskQueue taskQueue;

    public DashboardController(TaskQueue taskQueue) {
        this.taskQueue = taskQueue;
    }

    /**
     * Returns queue statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("queueSize", taskQueue.size());
        stats.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(stats);
    }

    /**
     * Returns a task by its ID.
     */
    @GetMapping("/tasks/{id}")
    public ResponseEntity<Task> getTask(@PathVariable String id) {
        Optional<Task> task = taskQueue.getById(id);
        return task.map(ResponseEntity::ok)
                   .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Cancels a task by its ID.
     */
    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Map<String, Object>> cancelTask(@PathVariable String id) {
        boolean cancelled = taskQueue.cancel(id);
        Map<String, Object> response = new HashMap<>();
        response.put("taskId", id);
        response.put("cancelled", cancelled);
        return ResponseEntity.ok(response);
    }
}
