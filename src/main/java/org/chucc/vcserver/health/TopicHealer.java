package org.chucc.vcserver.health;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.chucc.vcserver.config.KafkaProperties;
import org.chucc.vcserver.repository.BranchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

/**
 * Service to heal (recreate) missing Kafka topics.
 * Provides manual topic recreation with safeguards.
 */
@Service
public class TopicHealer {
  private static final Logger logger = LoggerFactory.getLogger(TopicHealer.class);

  private final BranchRepository branchRepository;
  private final KafkaTopicHealthChecker healthChecker;
  private final KafkaAdmin kafkaAdmin;
  private final KafkaProperties kafkaProperties;

  /**
   * Constructs a TopicHealer.
   *
   * @param branchRepository the branch repository
   * @param healthChecker the health checker
   * @param kafkaAdmin the Kafka admin client
   * @param kafkaProperties the Kafka properties
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "All dependencies are Spring-managed beans")
  public TopicHealer(
      BranchRepository branchRepository,
      KafkaTopicHealthChecker healthChecker,
      KafkaAdmin kafkaAdmin,
      KafkaProperties kafkaProperties) {
    this.branchRepository = branchRepository;
    this.healthChecker = healthChecker;
    this.kafkaAdmin = kafkaAdmin;
    this.kafkaProperties = kafkaProperties;
  }

  /**
   * Heals (recreates) a missing topic for a dataset.
   * Only recreates if the topic doesn't exist and the dataset has branches.
   *
   * @param dataset the dataset name
   * @return healing result
   */
  public HealingResult heal(String dataset) {
    List<String> actions = new ArrayList<>();

    try {
      // 1. Verify dataset exists (has branches)
      if (branchRepository.findAllByDataset(dataset).isEmpty()) {
        return HealingResult.failure(dataset,
            "Dataset does not exist (no branches found)");
      }

      String topicName = kafkaProperties.getTopicName(dataset);

      // 2. Check if topic exists
      if (healthChecker.topicExists(topicName)) {
        logger.info("Topic already exists for dataset: {} - no healing needed", dataset);
        return HealingResult.noActionNeeded(dataset);
      }

      // 3. Recreate missing topic
      logger.info("Recreating missing topic for dataset: {}", dataset);
      recreateTopic(dataset, topicName);
      actions.add("Recreated missing topic: " + topicName);

      logger.info("Successfully healed dataset: {}", dataset);
      return HealingResult.success(dataset, actions);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Healing interrupted for dataset: {}", dataset, e);
      return HealingResult.failure(dataset,
          "Healing interrupted: " + e.getMessage());
    } catch (java.util.concurrent.ExecutionException e) {
      logger.error("Failed to heal dataset: {}", dataset, e);
      return HealingResult.failure(dataset,
          "Healing failed: " + e.getMessage());
    } catch (RuntimeException e) {
      logger.error("Unexpected error healing dataset: {}", dataset, e);
      return HealingResult.failure(dataset,
          "Unexpected error: " + e.getMessage());
    }
  }

  /**
   * Recreates a missing topic with default configuration.
   *
   * @param dataset the dataset name
   * @param topicName the topic name
   * @throws InterruptedException if the operation is interrupted
   * @throws java.util.concurrent.ExecutionException if the creation fails
   */
  private void recreateTopic(String dataset, String topicName)
      throws InterruptedException, java.util.concurrent.ExecutionException {
    try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
      // Build topic configuration
      Map<String, String> config = new HashMap<>();
      config.put("retention.ms", String.valueOf(kafkaProperties.getRetentionMs()));
      config.put("cleanup.policy", kafkaProperties.isCompaction() ? "compact" : "delete");
      config.put("compression.type", "snappy");
      config.put("segment.ms", "604800000");  // 7 days
      config.put("max.message.bytes", "1048576");  // 1MB

      // Add production settings if RF > 1
      if (kafkaProperties.getReplicationFactor() > 1) {
        config.put("min.insync.replicas", "2");
        config.put("unclean.leader.election.enable", "false");
      }

      NewTopic newTopic = new NewTopic(
          topicName,
          kafkaProperties.getPartitions(),
          kafkaProperties.getReplicationFactor()
      );
      newTopic.configs(config);

      adminClient.createTopics(Collections.singletonList(newTopic)).all().get();

      logger.info("Topic recreated: {} (partitions={}, replication-factor={})",
          topicName, kafkaProperties.getPartitions(), kafkaProperties.getReplicationFactor());
    }
  }
}
