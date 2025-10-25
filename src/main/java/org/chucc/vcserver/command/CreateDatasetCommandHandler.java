package org.chucc.vcserver.command;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.core.mem.DatasetGraphInMemory;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.PolicyViolationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.chucc.vcserver.config.KafkaProperties;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.event.DatasetCreatedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.exception.DatasetAlreadyExistsException;
import org.chucc.vcserver.exception.kafka.InvalidKafkaConfigurationException;
import org.chucc.vcserver.exception.kafka.KafkaAuthorizationException;
import org.chucc.vcserver.exception.kafka.KafkaQuotaExceededException;
import org.chucc.vcserver.exception.kafka.KafkaUnavailableException;
import org.chucc.vcserver.exception.kafka.TopicCreationException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.service.DatasetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * Handles creation of new datasets with automatic Kafka topic creation.
 * Creates Kafka topic, initial commit, and main branch atomically.
 * Includes retry logic for transient Kafka failures.
 */
@Component
@EnableRetry
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class CreateDatasetCommandHandler implements CommandHandler<CreateDatasetCommand> {
  private static final Logger logger = LoggerFactory.getLogger(CreateDatasetCommandHandler.class);

  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;
  private final DatasetService datasetService;
  private final EventPublisher eventPublisher;
  private final KafkaAdmin kafkaAdmin;
  private final KafkaProperties kafkaProperties;
  private final MeterRegistry meterRegistry;

  /**
   * Constructs a CreateDatasetCommandHandler.
   *
   * @param branchRepository the branch repository
   * @param commitRepository the commit repository
   * @param datasetService the dataset service
   * @param eventPublisher the event publisher
   * @param kafkaAdmin the Kafka admin client
   * @param kafkaProperties the Kafka properties
   * @param meterRegistry the meter registry for metrics
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "All dependencies are Spring-managed beans")
  public CreateDatasetCommandHandler(
      BranchRepository branchRepository,
      CommitRepository commitRepository,
      DatasetService datasetService,
      EventPublisher eventPublisher,
      KafkaAdmin kafkaAdmin,
      KafkaProperties kafkaProperties,
      MeterRegistry meterRegistry) {
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
    this.datasetService = datasetService;
    this.eventPublisher = eventPublisher;
    this.kafkaAdmin = kafkaAdmin;
    this.kafkaProperties = kafkaProperties;
    this.meterRegistry = meterRegistry;
  }

  @Override
  @SuppressFBWarnings(
      value = "THROWS_METHOD_THROWS_RUNTIMEEXCEPTION",
      justification = "RuntimeException is the standard error pattern for command handlers")
  public VersionControlEvent handle(CreateDatasetCommand command) {
    String dataset = command.dataset();
    logger.info("Creating dataset: {}", dataset);

    Timer.Sample sample = Timer.start(meterRegistry);

    // 1. Check if dataset already exists (has branches)
    List<Branch> existingBranches = branchRepository.findAllByDataset(dataset);
    if (!existingBranches.isEmpty()) {
      throw new DatasetAlreadyExistsException(dataset);
    }

    boolean topicCreated = false;
    try {
      // 2. Create Kafka topic
      createKafkaTopic(dataset);
      topicCreated = true;

      // 3. Create initial empty commit
      Commit initialCommit = Commit.create(
          List.of(),
          command.author(),
          "Initial commit",
          0  // Empty patch size
      );

      // 4. Create empty patch for initial commit
      RDFPatch emptyPatch = RDFPatchOps.emptyPatch();

      // 5. Save commit and patch
      commitRepository.save(dataset, initialCommit, emptyPatch);

      // 6. Create main branch pointing to initial commit (PROTECTED by default)
      Branch mainBranch = new Branch(
          "main",
          initialCommit.id(),
          true,                    // main is protected
          Instant.now(),          // createdAt
          Instant.now(),          // lastUpdated
          1                       // initial commit count
      );
      branchRepository.save(dataset, mainBranch);

      // 7. Initialize empty dataset graph in cache
      DatasetGraphInMemory datasetGraph = new DatasetGraphInMemory();
      datasetService.cacheDatasetGraph(dataset, initialCommit.id(), datasetGraph);

      // 8. Create event
      DatasetCreatedEvent event = new DatasetCreatedEvent(
          dataset,
          "main",
          initialCommit.id().toString(),
          command.description().orElse(null),
          command.author(),
          Instant.now()
      );

      // 9. Publish event (async)
      eventPublisher.publish(event)
          .whenComplete((result, ex) -> {
            if (ex != null) {
              logger.error("Failed to publish event {} to Kafka: {}",
                  event.getClass().getSimpleName(), ex.getMessage(), ex);
            } else {
              logger.debug("Successfully published event {} to Kafka",
                  event.getClass().getSimpleName());
            }
          });

      logger.info("Dataset created: {} (topic: {}, initial commit: {})",
          dataset, kafkaProperties.getTopicName(dataset), initialCommit.id());

      // Record success metrics
      sample.stop(meterRegistry.timer("dataset.creation.time"));
      meterRegistry.counter("dataset.created").increment();

      return event;

    } catch (Exception e) {
      // Record failure metrics
      meterRegistry.counter("dataset.creation.failures",
          "reason", e.getClass().getSimpleName()
      ).increment();

      // Rollback: Delete topic if it was created
      if (topicCreated) {
        logger.warn("Dataset creation failed - rolling back topic creation: {}", dataset);
        try {
          deleteKafkaTopic(dataset);
        } catch (Exception rollbackEx) {
          logger.error("Failed to rollback topic deletion: {}", dataset, rollbackEx);
          // Manual cleanup may be required - log for ops
        }
      }

      // Re-throw exception
      if (e instanceof DatasetAlreadyExistsException || e instanceof IllegalArgumentException) {
        throw (RuntimeException) e;
      }
      throw new RuntimeException("Failed to create dataset: " + dataset, e);
    }
  }

  /**
   * Creates the Kafka topic for the dataset with retry logic for transient failures.
   *
   * @param dataset the dataset name
   * @throws KafkaUnavailableException if Kafka is unavailable (retried automatically)
   * @throws KafkaAuthorizationException if authorization fails (not retried)
   * @throws KafkaQuotaExceededException if quota is exceeded (not retried)
   * @throws InvalidKafkaConfigurationException if configuration is invalid (not retried)
   * @throws TopicCreationException for other failures
   */
  @Retryable(
      retryFor = {TimeoutException.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 5000)
  )
  private void createKafkaTopic(String dataset) {
    String topicName = kafkaProperties.getTopicName(dataset);
    Timer.Sample sample = Timer.start(meterRegistry);

    try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
      logger.info("Creating Kafka topic: {}", topicName);

      // Set topic configuration
      Map<String, String> config = new HashMap<>();
      config.put("retention.ms", String.valueOf(kafkaProperties.getRetentionMs()));
      config.put("cleanup.policy", kafkaProperties.isCompaction() ? "compact" : "delete");

      // Production settings (only if RF > 1)
      if (kafkaProperties.getReplicationFactor() > 1) {
        config.put("min.insync.replicas", "2");  // At least 2 replicas must ack
        config.put("unclean.leader.election.enable", "false");  // Prevent data loss
      }

      // Performance settings
      config.put("compression.type", "snappy");
      config.put("segment.ms", "604800000");  // 7 days per segment
      config.put("max.message.bytes", "1048576");  // 1MB max message

      NewTopic newTopic = new NewTopic(
          topicName,
          kafkaProperties.getPartitions(),
          kafkaProperties.getReplicationFactor()
      );
      newTopic.configs(config);

      adminClient.createTopics(Collections.singletonList(newTopic)).all().get();

      logger.info("Kafka topic created: {} (partitions={}, replication-factor={})",
          topicName, kafkaProperties.getPartitions(), kafkaProperties.getReplicationFactor());

      // Record success metrics
      sample.stop(meterRegistry.timer("kafka.topic.creation.time"));
      meterRegistry.counter("kafka.topic.created").increment();

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      meterRegistry.counter("kafka.topic.creation.failures",
          "reason", "InterruptedException"
      ).increment();
      throw new KafkaUnavailableException("Kafka operation interrupted: " + topicName, e);
    } catch (ExecutionException e) {
      meterRegistry.counter("kafka.topic.creation.failures",
          "reason", e.getCause().getClass().getSimpleName()
      ).increment();
      handleTopicCreationException(e, topicName, dataset);
    }
  }

  /**
   * Handles exceptions that occur during topic creation, mapping them to specific exception types.
   *
   * @param e the execution exception
   * @param topicName the topic name
   * @param dataset the dataset name
   */
  private void handleTopicCreationException(
      ExecutionException e, String topicName, String dataset) {
    Throwable cause = e.getCause();

    // Topic already exists - idempotent operation
    if (cause instanceof TopicExistsException) {
      logger.warn("Topic already exists: {} - checking dataset consistency", topicName);
      // Topic exists but dataset doesn't (orphaned topic) - this is handled by the
      // dataset existence check earlier. If we get here, it means the topic exists
      // but we don't have branches yet, which is a recoverable inconsistency.
      logger.info("Proceeding with dataset creation (topic already exists)");
      return;  // Continue with dataset creation
    }

    // Transient errors (will be retried by @Retryable)
    if (cause instanceof TimeoutException) {
      logger.warn("Kafka timeout when creating topic: {} (will retry)", topicName);
      throw new KafkaUnavailableException("Kafka cluster is not responding", cause);
    }

    // Authorization failure (fatal - do not retry)
    if (cause instanceof TopicAuthorizationException) {
      logger.error("CRITICAL: No permission to create topic: {}", topicName);
      throw new KafkaAuthorizationException(
          "Insufficient permissions to create Kafka topic", cause);
    }

    // Quota exceeded (fatal - do not retry)
    if (cause instanceof PolicyViolationException) {
      logger.warn("Kafka quota exceeded when creating topic: {}", topicName);
      throw new KafkaQuotaExceededException(
          "Kafka storage quota exceeded. Please contact administrator.", cause);
    }

    // Invalid replication factor (fatal - configuration error)
    if (cause instanceof InvalidReplicationFactorException) {
      logger.error("Invalid replication factor {} for topic: {} (insufficient brokers?)",
          kafkaProperties.getReplicationFactor(), topicName);
      throw new InvalidKafkaConfigurationException(
          "Replication factor exceeds available Kafka brokers", cause);
    }

    // Generic failure
    logger.error("Failed to create Kafka topic: {}", topicName, e);
    throw new TopicCreationException("Failed to create Kafka topic: " + topicName, cause);
  }

  /**
   * Recovery method called when retry attempts are exhausted.
   * Called automatically by Spring Retry framework.
   *
   * @param e the exception that caused retries to fail
   * @param dataset the dataset name
   */
  @Recover
  @SuppressFBWarnings(
      value = "UPM_UNCALLED_PRIVATE_METHOD",
      justification = "Called by Spring Retry framework via @Recover annotation")
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Called by Spring Retry framework
  private void recoverFromTopicCreationFailure(TimeoutException e, String dataset) {
    String topicName = kafkaProperties.getTopicName(dataset);
    logger.error("Failed to create topic after 3 retry attempts: {}", topicName, e);
    throw new KafkaUnavailableException(
        "Kafka cluster is unavailable after multiple attempts. Please try again later.", e);
  }

  /**
   * Deletes the Kafka topic for rollback purposes.
   *
   * @param dataset the dataset name
   */
  @SuppressFBWarnings(
      value = "REC_CATCH_EXCEPTION",
      justification = "Catch-all is intentional for rollback - must not fail main operation")
  private void deleteKafkaTopic(String dataset) {
    String topicName = kafkaProperties.getTopicName(dataset);

    try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
      logger.warn("Rolling back - deleting Kafka topic: {}", topicName);
      adminClient.deleteTopics(Collections.singletonList(topicName)).all().get();
      logger.info("Rollback successful - Kafka topic deleted: {}", topicName);
    } catch (Exception e) {
      logger.error("Rollback failed - could not delete Kafka topic: {}", topicName, e);
      // Don't throw - we've already logged the error
    }
  }
}
