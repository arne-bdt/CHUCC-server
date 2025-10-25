package org.chucc.vcserver.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Configuration validation tests for Kafka properties.
 */
class KafkaConfigTest {

  /**
   * Test that production profile loads correct Kafka configuration.
   */
  @SpringBootTest
  @ActiveProfiles("prod")
  static class ProductionConfigTest {

    @Autowired
    private KafkaProperties kafkaProperties;

    @Test
    void kafkaProperties_shouldLoadProductionConfig() {
      // Verify production topic settings
      assertThat(kafkaProperties.getReplicationFactor()).isEqualTo((short) 3);
      assertThat(kafkaProperties.getPartitions()).isEqualTo(6);

      // Verify production producer settings
      assertThat(kafkaProperties.getProducer().getAcks()).isEqualTo("all");
      assertThat(kafkaProperties.getProducer().isEnableIdempotence()).isTrue();
      assertThat(kafkaProperties.getProducer().getCompressionType()).isEqualTo("snappy");
      assertThat(kafkaProperties.getProducer().getRetries()).isEqualTo(3);
      assertThat(kafkaProperties.getProducer().getLingerMs()).isEqualTo(10);
      assertThat(kafkaProperties.getProducer().getBatchSize()).isEqualTo(32768);

      // Verify production consumer settings
      assertThat(kafkaProperties.getConsumer().getIsolationLevel()).isEqualTo("read_committed");
      assertThat(kafkaProperties.getConsumer().isEnableAutoCommit()).isTrue();
      assertThat(kafkaProperties.getConsumer().getAutoCommitIntervalMs()).isEqualTo(1000);
      assertThat(kafkaProperties.getConsumer().getMaxPollRecords()).isEqualTo(500);
    }
  }

  /**
   * Test that development profile loads correct Kafka configuration.
   */
  @SpringBootTest
  @ActiveProfiles("dev")
  static class DevelopmentConfigTest {

    @Autowired
    private KafkaProperties kafkaProperties;

    @Test
    void kafkaProperties_shouldLoadDevelopmentConfig() {
      // Verify development topic settings
      assertThat(kafkaProperties.getReplicationFactor()).isEqualTo((short) 1);
      assertThat(kafkaProperties.getPartitions()).isEqualTo(3);

      // Verify development producer settings (optimized for speed)
      assertThat(kafkaProperties.getProducer().getAcks()).isEqualTo("1");
      assertThat(kafkaProperties.getProducer().isEnableIdempotence()).isFalse();
      assertThat(kafkaProperties.getProducer().getCompressionType()).isEqualTo("none");
      assertThat(kafkaProperties.getProducer().getRetries()).isEqualTo(3);
      assertThat(kafkaProperties.getProducer().getLingerMs()).isEqualTo(0);
      assertThat(kafkaProperties.getProducer().getBatchSize()).isEqualTo(16384);

      // Verify development consumer settings
      assertThat(kafkaProperties.getConsumer().getIsolationLevel())
          .isEqualTo("read_uncommitted");
      assertThat(kafkaProperties.getConsumer().isEnableAutoCommit()).isTrue();
      assertThat(kafkaProperties.getConsumer().getAutoCommitIntervalMs()).isEqualTo(1000);
      assertThat(kafkaProperties.getConsumer().getMaxPollRecords()).isEqualTo(500);
    }
  }

  /**
   * Test that integration test profile loads correct Kafka configuration.
   */
  @SpringBootTest
  @ActiveProfiles("it")
  static class IntegrationTestConfigTest {

    @Autowired
    private KafkaProperties kafkaProperties;

    @Test
    void kafkaProperties_shouldLoadIntegrationTestConfig() {
      // Verify integration test topic settings (uses Testcontainers single broker)
      assertThat(kafkaProperties.getReplicationFactor()).isEqualTo((short) 1);
      assertThat(kafkaProperties.getPartitions()).isEqualTo(3);

      // Verify integration test producer settings (fast for tests)
      assertThat(kafkaProperties.getProducer().getAcks()).isEqualTo("1");
      assertThat(kafkaProperties.getProducer().isEnableIdempotence()).isFalse();
      assertThat(kafkaProperties.getProducer().getCompressionType()).isEqualTo("none");
      assertThat(kafkaProperties.getProducer().getRetries()).isEqualTo(3);

      // Verify integration test consumer settings
      assertThat(kafkaProperties.getConsumer().getIsolationLevel())
          .isEqualTo("read_uncommitted");
      assertThat(kafkaProperties.getConsumer().isEnableAutoCommit()).isTrue();
      assertThat(kafkaProperties.getConsumer().getAutoCommitIntervalMs()).isEqualTo(100);
      assertThat(kafkaProperties.getConsumer().getMaxPollRecords()).isEqualTo(500);
    }
  }
}
