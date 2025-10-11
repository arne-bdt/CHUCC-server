package org.chucc.vcserver.command;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.chucc.vcserver.config.KafkaProperties;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.DatasetDeletedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.exception.DatasetNotFoundException;
import org.chucc.vcserver.exception.UnconfirmedDeletionException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaAdmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DeleteDatasetCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class DeleteDatasetCommandHandlerTest {

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private CommitRepository commitRepository;

  @Mock
  private EventPublisher eventPublisher;

  @Mock
  private KafkaAdmin kafkaAdmin;

  @Mock
  private KafkaProperties kafkaProperties;

  @Mock
  private VersionControlProperties vcProperties;

  private DeleteDatasetCommandHandler handler;

  @BeforeEach
  void setUp() {
    // Mock EventPublisher.publish() to return completed future
    lenient().when(eventPublisher.publish(any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    handler = new DeleteDatasetCommandHandler(
        branchRepository,
        commitRepository,
        eventPublisher,
        kafkaAdmin,
        kafkaProperties,
        vcProperties
    );
  }

  @Test
  void shouldProduceDatasetDeletedEvent() {
    // Given
    String dataset = "test-dataset";
    CommitId commitId1 = CommitId.of("12345678-1234-1234-1234-123456789abc");
    CommitId commitId2 = CommitId.of("87654321-4321-4321-4321-987654321cba");
    Branch branch1 = new Branch("main", commitId1);
    Branch branch2 = new Branch("feature", commitId2);

    Commit commit1 = new Commit(commitId1, Collections.emptyList(), "alice", "First",
        Instant.now());
    Commit commit2 = new Commit(commitId2, Collections.emptyList(), "bob", "Second",
        Instant.now());

    DeleteDatasetCommand command = new DeleteDatasetCommand(
        dataset,
        "admin",
        false,
        true);

    when(branchRepository.findAllByDataset(dataset))
        .thenReturn(List.of(branch1, branch2));
    when(commitRepository.findAllByDataset(dataset))
        .thenReturn(List.of(commit1, commit2));

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertNotNull(event);
    assertEquals(DatasetDeletedEvent.class, event.getClass());

    DatasetDeletedEvent deletedEvent = (DatasetDeletedEvent) event;
    assertEquals(dataset, deletedEvent.dataset());
    assertEquals("admin", deletedEvent.author());
    assertEquals(2, deletedEvent.deletedBranches().size());
    assertEquals(2, deletedEvent.deletedCommitCount());
    assertFalse(deletedEvent.kafkaTopicDeleted());
    assertNotNull(deletedEvent.timestamp());
  }

  @Test
  void shouldRejectDeletionWithoutConfirmation() {
    // Given
    DeleteDatasetCommand command = new DeleteDatasetCommand(
        "test-dataset",
        "admin",
        false,
        false);  // Not confirmed

    // When/Then
    UnconfirmedDeletionException exception = assertThrows(
        UnconfirmedDeletionException.class,
        () -> handler.handle(command));

    assertEquals("Dataset deletion requires explicit confirmation (confirmed=true)",
        exception.getMessage());
  }

  @Test
  void shouldRejectDeletionOfNonExistentDataset() {
    // Given
    DeleteDatasetCommand command = new DeleteDatasetCommand(
        "non-existent",
        "admin",
        false,
        true);

    when(branchRepository.findAllByDataset("non-existent"))
        .thenReturn(Collections.emptyList());

    // When/Then
    DatasetNotFoundException exception = assertThrows(
        DatasetNotFoundException.class,
        () -> handler.handle(command));

    assertTrue(exception.getMessage().contains("Dataset not found or already empty"));
  }

  @Test
  void shouldAllowDeletionWithKafkaTopicFlag() {
    // Given
    String dataset = "test-dataset";
    CommitId commitId = CommitId.of("12345678-1234-1234-1234-123456789abc");
    Branch branch = new Branch("main", commitId);

    DeleteDatasetCommand command = new DeleteDatasetCommand(
        dataset,
        "admin",
        true,  // Request Kafka topic deletion
        true);

    when(branchRepository.findAllByDataset(dataset))
        .thenReturn(List.of(branch));
    when(commitRepository.findAllByDataset(dataset))
        .thenReturn(Collections.emptyList());
    when(vcProperties.isAllowKafkaTopicDeletion())
        .thenReturn(false);  // But config doesn't allow it

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertNotNull(event);
    DatasetDeletedEvent deletedEvent = (DatasetDeletedEvent) event;
    assertTrue(deletedEvent.kafkaTopicDeleted());  // Flag is recorded in event
    assertEquals(dataset, deletedEvent.dataset());
    assertEquals("admin", deletedEvent.author());
  }

  @Test
  void shouldRecordMultipleBranchesInEvent() {
    // Given
    String dataset = "multi-branch-dataset";
    Branch branch1 = new Branch("main", CommitId.generate());
    Branch branch2 = new Branch("develop", CommitId.generate());
    Branch branch3 = new Branch("feature", CommitId.generate());

    DeleteDatasetCommand command = new DeleteDatasetCommand(
        dataset,
        "admin",
        false,
        true);

    when(branchRepository.findAllByDataset(dataset))
        .thenReturn(List.of(branch1, branch2, branch3));
    when(commitRepository.findAllByDataset(dataset))
        .thenReturn(Collections.emptyList());

    // When
    DatasetDeletedEvent event = (DatasetDeletedEvent) handler.handle(command);

    // Then
    assertEquals(3, event.deletedBranches().size());
    assertTrue(event.deletedBranches().contains("main"));
    assertTrue(event.deletedBranches().contains("develop"));
    assertTrue(event.deletedBranches().contains("feature"));
  }
}
