package org.chucc.vcserver.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration for asynchronous task execution.
 * Provides a dedicated thread pool for snapshot creation.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

  /**
   * Thread pool executor for asynchronous snapshot creation.
   * Configured with a small pool size to prevent resource exhaustion
   * while allowing concurrent snapshot operations.
   *
   * @return ThreadPoolTaskExecutor for snapshot operations
   */
  @Bean(name = "snapshotExecutor")
  public Executor snapshotExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("snapshot-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);
    executor.initialize();
    return executor;
  }
}
