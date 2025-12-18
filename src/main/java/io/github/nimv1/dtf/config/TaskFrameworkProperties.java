package io.github.nimv1.dtf.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Distributed Task Framework.
 * 
 * <p>Configured via application.yml:</p>
 * <pre>{@code
 * dtf:
 *   enabled: true
 *   worker:
 *     concurrency: 4
 *     poll-interval-ms: 100
 *   queue:
 *     type: in-memory  # or redis, kafka
 * }</pre>
 * 
 * @author NiMv1
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "dtf")
public class TaskFrameworkProperties {

    private boolean enabled = true;
    private WorkerProperties worker = new WorkerProperties();
    private QueueProperties queue = new QueueProperties();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public WorkerProperties getWorker() { return worker; }
    public void setWorker(WorkerProperties worker) { this.worker = worker; }
    public QueueProperties getQueue() { return queue; }
    public void setQueue(QueueProperties queue) { this.queue = queue; }

    public static class WorkerProperties {
        private int concurrency = 4;
        private long pollIntervalMs = 100;
        private boolean autoStart = true;

        public int getConcurrency() { return concurrency; }
        public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
        public boolean isAutoStart() { return autoStart; }
        public void setAutoStart(boolean autoStart) { this.autoStart = autoStart; }
    }

    public static class QueueProperties {
        private String type = "in-memory";
        private String redisKeyPrefix = "dtf:";

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getRedisKeyPrefix() { return redisKeyPrefix; }
        public void setRedisKeyPrefix(String redisKeyPrefix) { this.redisKeyPrefix = redisKeyPrefix; }
    }
}
