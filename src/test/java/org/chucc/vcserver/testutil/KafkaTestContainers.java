package org.chucc.vcserver.testutil;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Centralized Kafka container configuration for integration tests.
 * Provides a consistent Kafka image version across all tests.
 */
public final class KafkaTestContainers {

  /**
   * Apache Kafka Docker image version for integration tests.
   * Using stable 3.9.1 release with KRaft mode (no ZooKeeper required).
   */
  private static final String KAFKA_IMAGE = "apache/kafka:3.9.1";

  private KafkaTestContainers() {
    // Utility class - prevent instantiation
  }

  /**
   * Creates a new Kafka container with standard configuration.
   * Container is configured with KRaft mode and standard ports.
   *
   * @return configured KafkaContainer instance
   */
  public static KafkaContainer createKafkaContainer() {
    return new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE));
  }

  /**
   * Creates a new Kafka container with reuse disabled.
   * Useful for tests that require isolation.
   *
   * @return configured KafkaContainer instance with reuse disabled
   */
  public static KafkaContainer createKafkaContainerNoReuse() {
    return new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE))
        .withReuse(false);
  }
}
