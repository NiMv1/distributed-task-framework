package io.github.nimv1.dtf.config;

import io.github.nimv1.dtf.client.TaskClient;
import io.github.nimv1.dtf.handler.TaskHandler;
import io.github.nimv1.dtf.metrics.TaskMetrics;
import io.github.nimv1.dtf.queue.InMemoryTaskQueue;
import io.github.nimv1.dtf.queue.RedisTaskQueue;
import io.github.nimv1.dtf.queue.TaskQueue;
import io.github.nimv1.dtf.worker.Worker;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Auto-configuration for the Distributed Task Framework.
 * 
 * @author NiMv1
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "dtf", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TaskFrameworkProperties.class)
public class TaskFrameworkAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TaskFrameworkAutoConfiguration.class);

    @Autowired
    private TaskFrameworkProperties properties;

    private Worker worker;

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "dtf.queue", name = "type", havingValue = "redis")
    @ConditionalOnBean(StringRedisTemplate.class)
    public TaskQueue redisTaskQueue(StringRedisTemplate redisTemplate) {
        log.info("Initializing Redis task queue with prefix: {}", properties.getQueue().getRedisKeyPrefix());
        return new RedisTaskQueue(redisTemplate, properties.getQueue().getRedisKeyPrefix());
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskQueue inMemoryTaskQueue() {
        log.info("Initializing in-memory task queue");
        return new InMemoryTaskQueue();
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskClient taskClient(TaskQueue taskQueue) {
        return new TaskClient(taskQueue);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    public TaskMetrics taskMetrics(MeterRegistry registry, TaskQueue taskQueue) {
        log.info("Initializing task metrics");
        return new TaskMetrics(registry, taskQueue);
    }

    @Bean
    @ConditionalOnMissingBean
    public Worker worker(TaskQueue taskQueue, List<TaskHandler> handlers) {
        Map<String, TaskHandler> handlerMap = handlers.stream()
                .collect(Collectors.toMap(TaskHandler::getTaskType, h -> h));
        
        log.info("Registered {} task handlers: {}", handlerMap.size(), handlerMap.keySet());
        
        this.worker = new Worker(taskQueue, handlerMap, properties.getWorker().getConcurrency());
        return this.worker;
    }

    @PostConstruct
    public void startWorker() {
        if (properties.getWorker().isAutoStart() && worker != null) {
            worker.start();
        }
    }

    @PreDestroy
    public void stopWorker() {
        if (worker != null && worker.isRunning()) {
            worker.stop();
        }
    }
}
