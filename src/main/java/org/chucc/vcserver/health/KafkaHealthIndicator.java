package org.chucc.vcserver.health;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.common.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Kafka connectivity.
 * Reports the health status of the Kafka cluster connection.
 */
@Component
public class KafkaHealthIndicator implements HealthIndicator {
  private static final Logger logger = LoggerFactory.getLogger(KafkaHealthIndicator.class);
  private static final int TIMEOUT_SECONDS = 5;

  private final KafkaAdmin kafkaAdmin;

  /**
   * Constructs a KafkaHealthIndicator.
   *
   * @param kafkaAdmin the Kafka admin client
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "KafkaAdmin is a Spring-managed bean")
  public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
    this.kafkaAdmin = kafkaAdmin;
  }

  @Override
  public Health health() {
    try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
      Collection<Node> nodes = adminClient.describeCluster()
          .nodes()
          .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

      logger.debug("Kafka health check successful: {} brokers available", nodes.size());

      return Health.up()
          .withDetail("brokers", nodes.size())
          .withDetail("status", "connected")
          .build();

    } catch (java.util.concurrent.TimeoutException e) {
      logger.warn("Kafka health check timeout after {} seconds", TIMEOUT_SECONDS);
      String timeoutMessage = "Kafka cluster did not respond within "
          + TIMEOUT_SECONDS + " seconds";
      return Health.down()
          .withDetail("status", "timeout")
          .withDetail("error", timeoutMessage)
          .build();

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Kafka health check interrupted", e);
      return Health.down()
          .withDetail("status", "interrupted")
          .withDetail("error", "Health check was interrupted")
          .build();

    } catch (Exception e) {
      logger.error("Kafka health check failed: {}", e.getMessage());
      return Health.down()
          .withDetail("status", "error")
          .withDetail("error", e.getMessage())
          .build();
    }
  }
}
