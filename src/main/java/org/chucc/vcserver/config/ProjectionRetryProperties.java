package org.chucc.vcserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for projection retry policy.
 *
 * <p>Controls how failed event projections are retried by Kafka consumer.
 * Uses exponential backoff strategy to handle transient failures gracefully.
 *
 * <p>Configuration example:
 * <pre>
 * chucc:
 *   projection:
 *     retry:
 *       max-attempts: 10           # Total retry attempts (default: 10)
 *       initial-interval: 1000     # Initial backoff in ms (default: 1s)
 *       multiplier: 2.0            # Exponential multiplier (default: 2.0)
 *       max-interval: 60000        # Max backoff in ms (default: 60s)
 * </pre>
 *
 * <p>Backoff progression (with defaults):
 * Attempt 1: 1s, Attempt 2: 2s, Attempt 3: 4s, Attempt 4: 8s,
 * Attempt 5: 16s, Attempt 6: 32s, Attempt 7-10: 60s (capped)
 */
@Configuration
@ConfigurationProperties(prefix = "chucc.projection.retry")
public class ProjectionRetryProperties {

  /**
   * Maximum number of retry attempts before sending to DLQ.
   * Default: 10 attempts.
   */
  private int maxAttempts = 10;

  /**
   * Initial backoff interval in milliseconds.
   * Default: 1000ms (1 second).
   */
  private long initialInterval = 1000;

  /**
   * Exponential backoff multiplier.
   * Each retry interval = previous interval × multiplier.
   * Default: 2.0 (doubles each time).
   */
  private double multiplier = 2.0;

  /**
   * Maximum backoff interval in milliseconds (cap).
   * Prevents exponential backoff from growing too large.
   * Default: 60000ms (60 seconds).
   */
  private long maxInterval = 60000;

  /**
   * Gets the maximum number of retry attempts.
   *
   * @return max retry attempts
   */
  public int getMaxAttempts() {
    return maxAttempts;
  }

  /**
   * Sets the maximum number of retry attempts.
   *
   * @param maxAttempts max retry attempts (must be &gt;= 1)
   */
  public void setMaxAttempts(int maxAttempts) {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be >= 1");
    }
    this.maxAttempts = maxAttempts;
  }

  /**
   * Gets the initial backoff interval in milliseconds.
   *
   * @return initial interval in ms
   */
  public long getInitialInterval() {
    return initialInterval;
  }

  /**
   * Sets the initial backoff interval in milliseconds.
   *
   * @param initialInterval initial interval in ms (must be &gt; 0)
   */
  public void setInitialInterval(long initialInterval) {
    if (initialInterval <= 0) {
      throw new IllegalArgumentException("initialInterval must be > 0");
    }
    this.initialInterval = initialInterval;
  }

  /**
   * Gets the exponential backoff multiplier.
   *
   * @return multiplier
   */
  public double getMultiplier() {
    return multiplier;
  }

  /**
   * Sets the exponential backoff multiplier.
   *
   * @param multiplier multiplier (must be &gt;= 1.0)
   */
  public void setMultiplier(double multiplier) {
    if (multiplier < 1.0) {
      throw new IllegalArgumentException("multiplier must be >= 1.0");
    }
    this.multiplier = multiplier;
  }

  /**
   * Gets the maximum backoff interval in milliseconds.
   *
   * @return max interval in ms
   */
  public long getMaxInterval() {
    return maxInterval;
  }

  /**
   * Sets the maximum backoff interval in milliseconds.
   *
   * @param maxInterval max interval in ms (must be &gt; 0)
   */
  public void setMaxInterval(long maxInterval) {
    if (maxInterval <= 0) {
      throw new IllegalArgumentException("maxInterval must be > 0");
    }
    this.maxInterval = maxInterval;
  }
}
