package org.chucc.vcserver.health;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.chucc.vcserver.config.KafkaProperties;
import org.chucc.vcserver.repository.BranchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

/**
 * Service to check the health of Kafka topics for all datasets.
 * Detects missing topics and provides health reports.
 */
@Service
public class KafkaTopicHealthChecker {
  private static final Logger logger = LoggerFactory.getLogger(KafkaTopicHealthChecker.class);

  private final BranchRepository branchRepository;
  private final KafkaAdmin kafkaAdmin;
  private final KafkaProperties kafkaProperties;

  /**
   * Constructs a KafkaTopicHealthChecker.
   *
   * @param branchRepository the branch repository
   * @param kafkaAdmin the Kafka admin client
   * @param kafkaProperties the Kafka properties
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "All dependencies are Spring-managed beans")
  public KafkaTopicHealthChecker(
      BranchRepository branchRepository,
      KafkaAdmin kafkaAdmin,
      KafkaProperties kafkaProperties) {
    this.branchRepository = branchRepository;
    this.kafkaAdmin = kafkaAdmin;
    this.kafkaProperties = kafkaProperties;
  }

  /**
   * Checks the health of all dataset topics.
   *
   * @return comprehensive health report
   */
  public AllTopicsHealthReport checkAll() {
    List<String> datasets = branchRepository.findAllDatasetNames();
    List<TopicHealthReport> reports = new ArrayList<>();

    for (String dataset : datasets) {
      TopicHealthReport report = checkTopic(dataset);
      reports.add(report);
    }

    return AllTopicsHealthReport.from(reports);
  }

  /**
   * Checks the health of a specific dataset's topic.
   *
   * @param dataset the dataset name
   * @return health report for the topic
   */
  public TopicHealthReport checkTopic(String dataset) {
    String topicName = kafkaProperties.getTopicName(dataset);

    try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
      // Check if topic exists
      Set<String> allTopics = adminClient.listTopics().names().get();

      if (!allTopics.contains(topicName)) {
        logger.warn("Topic missing for dataset: {} (expected topic: {})", dataset, topicName);
        return TopicHealthReport.missingTopic(dataset, topicName);
      }

      // Get topic details
      DescribeTopicsResult describeResult =
          adminClient.describeTopics(Set.of(topicName));
      TopicDescription description = describeResult.allTopicNames().get().get(topicName);

      int partitionCount = description.partitions().size();

      logger.debug("Topic healthy: {} (partitions: {})", topicName, partitionCount);
      return TopicHealthReport.healthy(dataset, topicName, partitionCount);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Health check interrupted for dataset: {}", dataset, e);
      return TopicHealthReport.error(dataset, topicName, "Health check interrupted");

    } catch (ExecutionException e) {
      logger.error("Failed to check topic health for dataset: {}", dataset, e);
      return TopicHealthReport.error(dataset, topicName, e.getCause().getMessage());

    } catch (Exception e) {
      logger.error("Unexpected error checking topic health for dataset: {}", dataset, e);
      return TopicHealthReport.error(dataset, topicName, "Unexpected error: " + e.getMessage());
    }
  }

  /**
   * Checks if a topic exists.
   *
   * @param topicName the topic name
   * @return true if the topic exists
   */
  public boolean topicExists(String topicName) {
    try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
      Set<String> topics = adminClient.listTopics().names().get();
      return topics.contains(topicName);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Topic existence check interrupted: {}", topicName, e);
      return false;
    } catch (ExecutionException e) {
      logger.error("Failed to check topic existence: {}", topicName, e);
      return false;
    }
  }
}
