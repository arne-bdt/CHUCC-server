package org.chucc.vcserver.command;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.chucc.vcserver.config.KafkaProperties;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.event.DatasetDeletedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.exception.DatasetNotFoundException;
import org.chucc.vcserver.exception.UnconfirmedDeletionException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/**
 * Handles deletion of entire datasets.
 * Deletes all branches, commits, and optionally the Kafka topic.
 */
@Component
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class DeleteDatasetCommandHandler implements CommandHandler<DeleteDatasetCommand> {
  private static final Logger logger = LoggerFactory.getLogger(DeleteDatasetCommandHandler.class);

  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;
  private final EventPublisher eventPublisher;
  private final KafkaAdmin kafkaAdmin;
  private final KafkaProperties kafkaProperties;
  private final VersionControlProperties vcProperties;

  /**
   * Constructs a DeleteDatasetCommandHandler.
   *
   * @param branchRepository the branch repository
   * @param commitRepository the commit repository
   * @param eventPublisher the event publisher
   * @param kafkaAdmin the Kafka admin client
   * @param kafkaProperties the Kafka properties
   * @param vcProperties the version control properties
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "All dependencies are Spring-managed beans")
  public DeleteDatasetCommandHandler(
      BranchRepository branchRepository,
      CommitRepository commitRepository,
      EventPublisher eventPublisher,
      KafkaAdmin kafkaAdmin,
      KafkaProperties kafkaProperties,
      VersionControlProperties vcProperties) {
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
    this.eventPublisher = eventPublisher;
    this.kafkaAdmin = kafkaAdmin;
    this.kafkaProperties = kafkaProperties;
    this.vcProperties = vcProperties;
  }

  @Override
  public VersionControlEvent handle(DeleteDatasetCommand command) {
    logger.warn("Deleting dataset: {} (deleteKafkaTopic={}, confirmed={})",
        command.dataset(), command.deleteKafkaTopic(), command.confirmed());

    // 1. Require confirmation
    if (!command.confirmed()) {
      throw new UnconfirmedDeletionException(
          "Dataset deletion requires explicit confirmation (confirmed=true)");
    }

    // 2. Check if dataset exists (has branches)
    List<Branch> branches = branchRepository.findAllByDataset(command.dataset());
    if (branches.isEmpty()) {
      throw new DatasetNotFoundException(
          "Dataset not found or already empty: " + command.dataset());
    }

    // 3. Count commits for audit
    int commitCount = commitRepository.findAllByDataset(command.dataset()).size();

    // 4. Create event BEFORE deletion (for audit trail)
    DatasetDeletedEvent event = new DatasetDeletedEvent(
        command.dataset(),
        command.author(),
        Instant.now(),
        branches.stream().map(Branch::getName).toList(),
        commitCount,
        command.deleteKafkaTopic()
    );

    // 5. Publish event (async)
    eventPublisher.publish(event)
        .exceptionally(ex -> {
          logger.error("Failed to publish event {}: {}",
              event.getClass().getSimpleName(), ex.getMessage(), ex);
          return null;
        });

    // 6. Optionally delete Kafka topic (destructive!)
    if (command.deleteKafkaTopic() && vcProperties.isAllowKafkaTopicDeletion()) {
      deleteKafkaTopic(command.dataset());
    } else if (command.deleteKafkaTopic()) {
      logger.warn("Kafka topic deletion requested but not allowed by configuration");
    }

    logger.warn("Dataset {} deleted: {} branches, {} commits, Kafka topic deleted: {}",
        command.dataset(), branches.size(), commitCount, command.deleteKafkaTopic());

    return event;
  }

  /**
   * Deletes the Kafka topic for the dataset.
   * This is destructive and irreversible!
   *
   * @param dataset the dataset name
   */
  private void deleteKafkaTopic(String dataset) {
    String topicName = kafkaProperties.getTopicName(dataset);

    try {
      logger.warn("Deleting Kafka topic: {}", topicName);

      AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties());
      try {
        DeleteTopicsResult result = adminClient.deleteTopics(Collections.singletonList(topicName));
        result.all().get();  // Wait for deletion to complete

        logger.warn("Kafka topic deleted: {}", topicName);
      } finally {
        adminClient.close();
      }
    } catch (InterruptedException e) {
      logger.error("Failed to delete Kafka topic: {}", topicName, e);
      Thread.currentThread().interrupt();
      // Don't throw - deletion already recorded in event
    } catch (ExecutionException e) {
      logger.error("Failed to delete Kafka topic: {}", topicName, e);
      // Don't throw - deletion already recorded in event
    }
  }
}
