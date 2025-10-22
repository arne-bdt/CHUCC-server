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
   * Gets the Kafka listener configuration.
   *
   * @return the Kafka listener configuration
   */
  public KafkaListener getKafkaListener() {
    return kafkaListener;
  }

  /**
   * Sets the Kafka listener configuration.
   *
   * @param kafkaListener the Kafka listener configuration
   */
  public void setKafkaListener(KafkaListener kafkaListener) {
    this.kafkaListener = kafkaListener;
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
}
