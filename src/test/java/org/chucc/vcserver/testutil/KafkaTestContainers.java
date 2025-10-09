package org.chucc.vcserver.testutil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Centralized Kafka container configuration for integration tests.
 * Reads configuration from test.properties for maintainability.
 * Uses singleton pattern to share one container across all tests.
 */
public final class KafkaTestContainers {

  private static final Properties TEST_PROPERTIES = loadTestProperties();
  private static final String KAFKA_IMAGE = TEST_PROPERTIES.getProperty(
      "testcontainers.kafka.image",
      "apache/kafka-native:4.1.0" // fallback default - native image for faster tests
  );
  private static final boolean KAFKA_REUSE = Boolean.parseBoolean(
      TEST_PROPERTIES.getProperty("testcontainers.kafka.reuse", "false")
  );

  private static KafkaContainer sharedContainer;
  private static boolean containerStarted = false;

  private KafkaTestContainers() {
    // Utility class - prevent instantiation
  }

  /**
   * Gets the shared Kafka container instance, creating and starting it if needed.
   * All integration tests share this single container to avoid resource exhaustion.
   * Container is configured with KRaft mode and standard ports.
   * Configuration is loaded from test.properties.
   *
   * @return shared KafkaContainer instance
   */
  public static synchronized KafkaContainer createKafkaContainer() {
    if (sharedContainer == null) {
      sharedContainer = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE));
    }
    if (!containerStarted) {
      sharedContainer.start();
      containerStarted = true;
      // Register shutdown hook to stop container when JVM exits
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        if (sharedContainer != null && sharedContainer.isRunning()) {
          sharedContainer.stop();
        }
      }));
    }
    return sharedContainer;
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

  /**
   * Gets the configured Kafka Docker image name.
   *
   * @return Kafka image name from test.properties
   */
  public static String getKafkaImage() {
    return KAFKA_IMAGE;
  }

  /**
   * Loads test properties from test.properties file.
   *
   * @return Properties object with test configuration
   */
  private static Properties loadTestProperties() {
    Properties props = new Properties();
    try (InputStream input = KafkaTestContainers.class.getClassLoader()
        .getResourceAsStream("test.properties")) {
      if (input != null) {
        props.load(input);
      }
    } catch (IOException e) {
      // If properties file not found, use defaults
      System.err.println("Warning: Could not load test.properties, using defaults");
    }
    return props;
  }
}
