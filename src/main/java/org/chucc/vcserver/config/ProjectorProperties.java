package org.chucc.vcserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the read model projector.
 */
@Component
@ConfigurationProperties(prefix = "projector")
public class ProjectorProperties {
  /**
   * Kafka listener configuration for the read model projector.
   */
  private KafkaListener kafkaListener = new KafkaListener();

  /**
   * Deduplication configuration for the read model projector.
   */
  private Deduplication deduplication = new Deduplication();

  /**
   * Gets the Kafka listener configuration.
   * Note: This returns the actual internal object (not a copy) as required by Spring Boot
   * configuration properties binding. The EI_EXPOSE_REP warning is suppressed because
   * Spring Boot requires direct access to bind nested properties.
   *
   * @return the Kafka listener configuration
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Spring Boot ConfigurationProperties requires direct access"
          + " to nested objects")
  public KafkaListener getKafkaListener() {
    return kafkaListener;
  }

  /**
   * Sets the Kafka listener configuration.
   * Note: This stores the external object directly (not a copy) as required by Spring Boot
   * configuration properties binding. The EI_EXPOSE_REP2 warning is suppressed because
   * Spring Boot requires direct access to bind nested properties.
   *
   * @param kafkaListener the Kafka listener configuration
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring Boot ConfigurationProperties requires direct access"
          + " to nested objects")
  public void setKafkaListener(KafkaListener kafkaListener) {
    this.kafkaListener = kafkaListener;
  }

  /**
   * Gets the deduplication configuration.
   * Note: This returns the actual internal object (not a copy) as required by Spring Boot
   * configuration properties binding. The EI_EXPOSE_REP warning is suppressed because
   * Spring Boot requires direct access to bind nested properties.
   *
   * @return the deduplication configuration
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Spring Boot ConfigurationProperties requires direct access"
          + " to nested objects")
  public Deduplication getDeduplication() {
    return deduplication;
  }

  /**
   * Sets the deduplication configuration.
   * Note: This stores the external object directly (not a copy) as required by Spring Boot
   * configuration properties binding. The EI_EXPOSE_REP2 warning is suppressed because
   * Spring Boot requires direct access to bind nested properties.
   *
   * @param deduplication the deduplication configuration
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring Boot ConfigurationProperties requires direct access"
          + " to nested objects")
  public void setDeduplication(Deduplication deduplication) {
    this.deduplication = deduplication;
  }

  /**
   * Kafka listener configuration.
   */
  public static class KafkaListener {
    /**
     * Whether the Kafka listener is enabled.
     * Default is true in production, false in integration tests.
     */
    private boolean enabled = true;

    /**
     * Checks if the Kafka listener is enabled.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
      return enabled;
    }

    /**
     * Sets whether the Kafka listener is enabled.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  /**
   * Deduplication configuration.
   */
  public static class Deduplication {
    /**
     * Maximum number of event IDs to cache for deduplication.
     * Default: 100,000 event IDs (~5-10 MB RAM).
     */
    private int cacheSize = 100_000;

    /**
     * Enable/disable deduplication.
     * Default: true (always deduplicate).
     */
    private boolean enabled = true;

    /**
     * Returns the cache size.
     *
     * @return the maximum number of event IDs to cache
     */
    public int getCacheSize() {
      return cacheSize;
    }

    /**
     * Sets the cache size.
     *
     * @param cacheSize the maximum number of event IDs to cache
     */
    public void setCacheSize(int cacheSize) {
      this.cacheSize = cacheSize;
    }

    /**
     * Returns whether deduplication is enabled.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
      return enabled;
    }

    /**
     * Sets whether deduplication is enabled.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }
}
