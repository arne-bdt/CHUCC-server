package org.chucc.vcserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for dataset graph caching.
 * Controls the behavior of the LRU cache used to store materialized dataset graphs.
 */
@Component
@ConfigurationProperties(prefix = "vc.cache")
public class CacheProperties {

  /**
   * Maximum number of cached dataset graphs (excluding latest per branch).
   * When this limit is reached, least recently used entries are evicted.
   */
  private int maxSize = 100;

  /**
   * Whether to always keep latest commit per branch in cache (never evict).
   * This ensures that queries to branch HEADs are always fast.
   */
  private boolean keepLatestPerBranch = true;

  /**
   * Time-to-live for cached graphs in minutes (0 = no TTL).
   * Entries are evicted after this period of inactivity.
   */
  private int ttlMinutes = 0;

  /**
   * Gets the maximum number of cached dataset graphs.
   *
   * @return the maximum cache size
   */
  public int getMaxSize() {
    return maxSize;
  }

  /**
   * Sets the maximum number of cached dataset graphs.
   *
   * @param maxSize the maximum cache size
   */
  public void setMaxSize(int maxSize) {
    this.maxSize = maxSize;
  }

  /**
   * Checks if latest commits per branch should be kept in cache.
   *
   * @return true if latest commits are pinned in cache
   */
  public boolean isKeepLatestPerBranch() {
    return keepLatestPerBranch;
  }

  /**
   * Sets whether to keep latest commits per branch in cache.
   *
   * @param keepLatestPerBranch true to pin latest commits in cache
   */
  public void setKeepLatestPerBranch(boolean keepLatestPerBranch) {
    this.keepLatestPerBranch = keepLatestPerBranch;
  }

  /**
   * Gets the time-to-live for cached graphs in minutes.
   *
   * @return the TTL in minutes (0 = no TTL)
   */
  public int getTtlMinutes() {
    return ttlMinutes;
  }

  /**
   * Sets the time-to-live for cached graphs in minutes.
   *
   * @param ttlMinutes the TTL in minutes (0 = no TTL)
   */
  public void setTtlMinutes(int ttlMinutes) {
    this.ttlMinutes = ttlMinutes;
  }
}
