package org.chucc.vcserver.config;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties(AsyncConfig.SnapshotExecutorProperties.class)
public class AsyncConfig {

  private final SnapshotExecutorProperties properties;

  /**
   * Constructor for AsyncConfig.
   *
   * @param properties snapshot executor properties
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "SnapshotExecutorProperties is a Spring-managed configuration bean")
  public AsyncConfig(SnapshotExecutorProperties properties) {
    this.properties = properties;
  }

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
    executor.setCorePoolSize(properties.corePoolSize);
    executor.setMaxPoolSize(properties.maxPoolSize);
    executor.setQueueCapacity(properties.queueCapacity);
    executor.setThreadNamePrefix("snapshot-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(properties.awaitTerminationSeconds);
    executor.initialize();
    return executor;
  }

  /**
   * Configuration properties for snapshot executor thread pool.
   */
  @ConfigurationProperties(prefix = "async.snapshot-executor")
  public static class SnapshotExecutorProperties {
    private int corePoolSize = 2;
    private int maxPoolSize = 4;
    private int queueCapacity = 100;
    private int awaitTerminationSeconds = 60;

    public int getCorePoolSize() {
      return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
      this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
      return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
      this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
      return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
      this.queueCapacity = queueCapacity;
    }

    public int getAwaitTerminationSeconds() {
      return awaitTerminationSeconds;
    }

    public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
      this.awaitTerminationSeconds = awaitTerminationSeconds;
    }
  }
}
