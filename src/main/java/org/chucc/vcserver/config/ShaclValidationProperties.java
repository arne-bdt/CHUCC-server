package org.chucc.vcserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for SHACL validation.
 *
 * <p>Basic resource limits for MVP. Advanced features (remote endpoints, caching)
 * will add additional configuration sections in later tasks.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "chucc.shacl.validation")
public class ShaclValidationProperties {

  /** Default maximum shapes size in bytes (10 MB). */
  private static final int DEFAULT_MAX_SHAPES_SIZE = 10485760;

  /** Default maximum data size in bytes (100 MB). */
  private static final int DEFAULT_MAX_DATA_SIZE = 104857600;

  /** Default timeout in milliseconds (60 seconds). */
  private static final int DEFAULT_TIMEOUT = 60000;

  /** Default maximum concurrent validations. */
  private static final int DEFAULT_MAX_CONCURRENT = 10;

  private boolean enabled = true;
  private int maxShapesSize = DEFAULT_MAX_SHAPES_SIZE;
  private int maxDataSize = DEFAULT_MAX_DATA_SIZE;
  private int timeout = DEFAULT_TIMEOUT;
  private int maxConcurrent = DEFAULT_MAX_CONCURRENT;

  /**
   * Check if SHACL validation is enabled.
   *
   * @return true if enabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Set whether SHACL validation is enabled.
   *
   * @param enabled true to enable
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Get maximum shapes graph size in bytes.
   *
   * @return maximum size in bytes
   */
  public int getMaxShapesSize() {
    return maxShapesSize;
  }

  /**
   * Set maximum shapes graph size in bytes.
   *
   * @param maxShapesSize maximum size in bytes
   */
  public void setMaxShapesSize(int maxShapesSize) {
    this.maxShapesSize = maxShapesSize;
  }

  /**
   * Get maximum data graph size in bytes.
   *
   * @return maximum size in bytes
   */
  public int getMaxDataSize() {
    return maxDataSize;
  }

  /**
   * Set maximum data graph size in bytes.
   *
   * @param maxDataSize maximum size in bytes
   */
  public void setMaxDataSize(int maxDataSize) {
    this.maxDataSize = maxDataSize;
  }

  /**
   * Get validation timeout in milliseconds.
   *
   * @return timeout in milliseconds
   */
  public int getTimeout() {
    return timeout;
  }

  /**
   * Set validation timeout in milliseconds.
   *
   * @param timeout timeout in milliseconds
   */
  public void setTimeout(int timeout) {
    this.timeout = timeout;
  }

  /**
   * Get maximum number of concurrent validations.
   *
   * @return maximum concurrent validations
   */
  public int getMaxConcurrent() {
    return maxConcurrent;
  }

  /**
   * Set maximum number of concurrent validations.
   *
   * @param maxConcurrent maximum concurrent validations
   */
  public void setMaxConcurrent(int maxConcurrent) {
    this.maxConcurrent = maxConcurrent;
  }
}
