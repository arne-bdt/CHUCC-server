package org.chucc.vcserver.config;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.chucc.vcserver.event.VersionControlEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Kafka configuration for event streaming.
 * Configures producers, admin client, and topic settings.
 */
@Configuration
public class KafkaConfig {

  private final KafkaProperties kafkaProperties;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "KafkaProperties is a Spring configuration bean, "
          + "immutable after initialization")
  public KafkaConfig(KafkaProperties kafkaProperties) {
    this.kafkaProperties = kafkaProperties;
  }

  /**
   * Kafka admin client for topic management.
   * Auto-startup is disabled to prevent blocking on Kafka connection during application startup.
   *
   * @return KafkaAdmin instance
   */
  @Bean
  public KafkaAdmin kafkaAdmin() {
    Map<String, Object> configs = new HashMap<>();
    configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
        kafkaProperties.getBootstrapServers());
    KafkaAdmin admin = new KafkaAdmin(configs);
    admin.setAutoCreate(false); // Disable auto-creation of topics on startup
    return admin;
  }

  /**
   * Producer factory for version control events.
   * Configured with idempotence and optional transactional support for exactly-once semantics.
   *
   * @return ProducerFactory instance
   */
  @Bean
  public ProducerFactory<String, VersionControlEvent> producerFactory() {
    Map<String, Object> configProps = new HashMap<>();
    configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
        kafkaProperties.getBootstrapServers());
    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class);
    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        JsonSerializer.class);
    configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
    configProps.put(ProducerConfig.ACKS_CONFIG, "all");
    configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
    configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

    // Transactional producer configuration (optional)
    DefaultKafkaProducerFactory<String, VersionControlEvent> factory =
        new DefaultKafkaProducerFactory<>(configProps);

    // Enable transactions if transactional ID prefix is configured
    if (kafkaProperties.getTransactionalIdPrefix() != null
        && !kafkaProperties.getTransactionalIdPrefix().isEmpty()) {
      factory.setTransactionIdPrefix(kafkaProperties.getTransactionalIdPrefix());
    }

    return factory;
  }

  /**
   * Kafka template for sending version control events.
   *
   * @return KafkaTemplate instance
   */
  @Bean
  public KafkaTemplate<String, VersionControlEvent> kafkaTemplate() {
    return new KafkaTemplate<>(producerFactory());
  }

  /**
   * Creates a template topic configuration for version control events.
   * This is not auto-created, but serves as a reference for creating topics.
   *
   * @return NewTopic template
   */
  @Bean
  public NewTopic versionControlEventTopicTemplate() {
    TopicBuilder builder = TopicBuilder
        .name(kafkaProperties.getTopicTemplate())
        .partitions(kafkaProperties.getPartitions())
        .replicas(kafkaProperties.getReplicationFactor());

    // Configure retention
    if (kafkaProperties.getRetentionMs() > 0) {
      builder.config(TopicConfig.RETENTION_MS_CONFIG,
          String.valueOf(kafkaProperties.getRetentionMs()));
    } else {
      // Infinite retention
      builder.config(TopicConfig.RETENTION_MS_CONFIG, "-1");
    }

    // Configure compaction (off for append-only)
    if (kafkaProperties.isCompaction()) {
      builder.config(TopicConfig.CLEANUP_POLICY_CONFIG,
          TopicConfig.CLEANUP_POLICY_COMPACT);
    } else {
      builder.config(TopicConfig.CLEANUP_POLICY_CONFIG,
          TopicConfig.CLEANUP_POLICY_DELETE);
    }

    // Additional topic configurations
    builder.config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1");
    builder.config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy");

    return builder.build();
  }

  /**
   * Consumer factory for version control events.
   * Configured with auto.offset.reset=earliest for startup recovery.
   *
   * @return ConsumerFactory instance
   */
  @Bean
  public ConsumerFactory<String, VersionControlEvent> consumerFactory() {
    Map<String, Object> configProps = new HashMap<>();
    configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        kafkaProperties.getBootstrapServers());
    configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "read-model-projector");
    configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        StringDeserializer.class);
    configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        JsonDeserializer.class);
    configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "org.chucc.vcserver.event");
    configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
        "org.chucc.vcserver.event.VersionControlEvent");
    configProps.put(JsonDeserializer.TYPE_MAPPINGS,
        "BranchCreated:org.chucc.vcserver.event.BranchCreatedEvent,"
        + "CommitCreated:org.chucc.vcserver.event.CommitCreatedEvent,"
        + "TagCreated:org.chucc.vcserver.event.TagCreatedEvent,"
        + "BranchReset:org.chucc.vcserver.event.BranchResetEvent,"
        + "RevertCreated:org.chucc.vcserver.event.RevertCreatedEvent,"
        + "SnapshotCreated:org.chucc.vcserver.event.SnapshotCreatedEvent");

    // Start from earliest offset on startup for recovery
    configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    // Enable auto-commit for simplicity (at-least-once delivery)
    configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
    configProps.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 1000);

    return new DefaultKafkaConsumerFactory<>(configProps);
  }

  /**
   * Kafka listener container factory for event consumers.
   *
   * @return ConcurrentKafkaListenerContainerFactory instance
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, VersionControlEvent>
      kafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, VersionControlEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory());
    factory.setConcurrency(1); // Single consumer for ordered processing
    return factory;
  }
}
