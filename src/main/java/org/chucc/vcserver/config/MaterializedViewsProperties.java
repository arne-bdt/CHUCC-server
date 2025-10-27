package org.chucc.vcserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for materialized views.
 *
 * <p>These properties control the behavior of the MaterializedBranchRepository
 * and related monitoring components.
 *
 * <p>Configuration prefix: {@code chucc.materialized-views}
 *
 * <p>Example configuration in {@code application.yml}:
 * <pre>
 * chucc:
 *   materialized-views:
 *     enabled: true
 *     max-branches: 25
 *     cache-stats-enabled: true
 *     memory-warning-threshold-mb: 1000
 *     periodic-logging-enabled: true
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "chucc.materialized-views")
public class MaterializedViewsProperties {

  private boolean enabled = true;
  private int maxBranches = 25;
  private boolean cacheStatsEnabled = true;
  private long memoryWarningThresholdMb = 1000;
  private boolean periodicLoggingEnabled = true;

  /**
   * Check if materialized views are enabled.
   *
   * @return true if enabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Set whether materialized views are enabled.
   *
   * @param enabled true to enable
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Get the memory warning threshold in megabytes.
   *
   * <p>When materialized views exceed this threshold, a warning is logged.
   *
   * @return threshold in MB
   */
  public long getMemoryWarningThresholdMb() {
    return memoryWarningThresholdMb;
  }

  /**
   * Set the memory warning threshold in megabytes.
   *
   * @param memoryWarningThresholdMb threshold in MB
   */
  public void setMemoryWarningThresholdMb(long memoryWarningThresholdMb) {
    this.memoryWarningThresholdMb = memoryWarningThresholdMb;
  }

  /**
   * Check if periodic logging is enabled.
   *
   * @return true if periodic logging is enabled
   */
  public boolean isPeriodicLoggingEnabled() {
    return periodicLoggingEnabled;
  }

  /**
   * Set whether periodic logging is enabled.
   *
   * @param periodicLoggingEnabled true to enable
   */
  public void setPeriodicLoggingEnabled(boolean periodicLoggingEnabled) {
    this.periodicLoggingEnabled = periodicLoggingEnabled;
  }

  /**
   * Get the maximum number of branches to keep in cache.
   *
   * <p>When this limit is exceeded, least recently used branches are evicted.
   * Evicted branches are rebuilt on-demand when accessed.
   *
   * @return maximum number of branches (default: 25)
   */
  public int getMaxBranches() {
    return maxBranches;
  }

  /**
   * Set the maximum number of branches to keep in cache.
   *
   * @param maxBranches maximum branches (must be >= 1)
   * @throws IllegalArgumentException if maxBranches &lt; 1
   */
  public void setMaxBranches(int maxBranches) {
    final int minMaxBranches = 1;
    if (maxBranches < minMaxBranches) {
      throw new IllegalArgumentException(
          "maxBranches must be >= " + minMaxBranches + " (got: " + maxBranches + ")");
    }
    this.maxBranches = maxBranches;
  }

  /**
   * Check if cache statistics are enabled.
   *
   * <p>When enabled, Caffeine cache records detailed statistics
   * (hits, misses, evictions, load times) for monitoring.
   *
   * @return true if cache stats enabled (default: true)
   */
  public boolean isCacheStatsEnabled() {
    return cacheStatsEnabled;
  }

  /**
   * Set whether cache statistics are enabled.
   *
   * @param cacheStatsEnabled true to enable cache stats
   */
  public void setCacheStatsEnabled(boolean cacheStatsEnabled) {
    this.cacheStatsEnabled = cacheStatsEnabled;
  }
}
