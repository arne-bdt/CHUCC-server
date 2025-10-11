package org.chucc.vcserver.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.chucc.vcserver.command.DeleteDatasetCommand;
import org.chucc.vcserver.command.DeleteDatasetCommandHandler;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.DatasetDeletedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.testutil.KafkaTestContainers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integration test for dataset deletion with Kafka topic deletion.
 * Verifies that events record the actual outcome of Kafka topic deletion attempts.
 */
@SpringBootTest
@ActiveProfiles("it")
class DatasetDeletionIntegrationTest {

  // Eager initialization - container must be started before @DynamicPropertySource
  private static KafkaContainer kafkaContainer = KafkaTestContainers.createKafkaContainer();

  @Autowired
  private DeleteDatasetCommandHandler deleteDatasetCommandHandler;

  @Autowired
  private BranchRepository branchRepository;

  @DynamicPropertySource
  static void configureKafka(DynamicPropertyRegistry registry) {
    registry.add("kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    registry.add("vc.allow-kafka-topic-deletion", () -> "true");
  }

  @Test
  void shouldRecordFalseWhenKafkaTopicDeletionFails() {
    // Given: Dataset with non-existent Kafka topic
    String dataset = "test-dataset-" + System.currentTimeMillis();

    // Add branches to repository so dataset "exists"
    Branch branch = new Branch("main", CommitId.generate());
    branchRepository.save(dataset, branch);

    // When: Delete dataset with Kafka topic deletion (will fail - topic doesn't exist)
    DeleteDatasetCommand command = new DeleteDatasetCommand(
        dataset,
        "admin",
        true,  // Request Kafka topic deletion
        true   // Confirmed
    );

    VersionControlEvent event = deleteDatasetCommandHandler.handle(command);

    // Then: Event should record kafkaTopicDeleted=false because deletion failed
    // This verifies the fix where events record ACTUAL outcomes, not predicted outcomes
    assertNotNull(event);
    DatasetDeletedEvent deletedEvent = (DatasetDeletedEvent) event;
    assertFalse(deletedEvent.kafkaTopicDeleted(),
        "Event should record false when Kafka topic deletion fails");
  }
}
