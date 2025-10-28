package org.chucc.vcserver.projection;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.chucc.vcserver.config.ProjectorProperties;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.BranchCreatedEvent;
import org.chucc.vcserver.event.BranchResetEvent;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.chucc.vcserver.repository.TagRepository;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.SnapshotService;
import org.chucc.vcserver.testutil.ExpectedErrorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ReadModelProjector.
 * Tests event handling and state tracking logic.
 */
@ExtendWith(MockitoExtension.class)
class ReadModelProjectorTest {

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private CommitRepository commitRepository;

  @Mock
  private TagRepository tagRepository;

  @Mock
  private MaterializedBranchRepository materializedBranchRepo;

  @Mock
  private DatasetService datasetService;

  @Mock
  private SnapshotService snapshotService;

  @Mock
  private ProjectorProperties projectorProperties;

  @Mock
  private MeterRegistry meterRegistry;

  @Mock
  private Counter mockCounter;

  private ReadModelProjector projector;

  @BeforeEach
  void setUp() {
    // Configure deduplication properties
    ProjectorProperties.Deduplication dedup = new ProjectorProperties.Deduplication();
    dedup.setEnabled(false); // Disable deduplication for unit tests
    when(projectorProperties.getDeduplication()).thenReturn(dedup);

    // Stub MeterRegistry to return mock Counter (lenient = optional stubbing)
    lenient().when(meterRegistry.counter(any(String.class), any(String[].class)))
        .thenReturn(mockCounter);

    projector = new ReadModelProjector(branchRepository, commitRepository, tagRepository,
        materializedBranchRepo, datasetService, snapshotService, projectorProperties, meterRegistry);
  }

  /**
   * Helper method to wrap an event in a ConsumerRecord for testing.
   */
  private ConsumerRecord<String, VersionControlEvent> toConsumerRecord(VersionControlEvent event) {
    return new ConsumerRecord<String, VersionControlEvent>(
        "test-topic",
        0,
        0L,
        event.getAggregateIdentity().getPartitionKey(),
        event
    );
  }

  @Test
  void handleCommitCreatedEvent_shouldSaveCommitAndPatch() {
    // Given
    String dataset = "test-dataset";
    String commitIdStr = "550e8400-e29b-41d4-a716-446655440000";
    String rdfPatchStr = "TX .";

    CommitCreatedEvent event = new CommitCreatedEvent(
        dataset,
        commitIdStr,
        List.of(), null,
        "Test commit",
        "test-author",
        Instant.now(),
        rdfPatchStr
        , 1
    );

    // When
    projector.handleCommitCreated(event);

    // Then
    verify(commitRepository).save(eq(dataset), any(Commit.class), any(RDFPatch.class));
  }

  @Test
  void handleBranchCreatedEvent_shouldSaveBranch() {
    // Given
    String dataset = "test-dataset";
    String branchName = "feature-branch";
    String commitIdStr = "550e8400-e29b-41d4-a716-446655440000";

    BranchCreatedEvent event = new BranchCreatedEvent(
        dataset,
        branchName,
        commitIdStr,
        "main",
        false,
        "test-author",
        Instant.now()
    );

    // When
    projector.handleBranchCreated(event);

    // Then
    verify(branchRepository).save(eq(dataset), any(Branch.class));
  }

  @Test
  void handleBranchResetEvent_shouldUpdateBranchHead() {
    // Given
    String dataset = "test-dataset";
    String branchName = "main";
    String fromCommitIdStr = "550e8400-e29b-41d4-a716-446655440000";
    String toCommitIdStr = "660e8400-e29b-41d4-a716-446655440001";

    // Stub branch existence check
    Branch existingBranch = new Branch(branchName, CommitId.of(fromCommitIdStr));
    when(branchRepository.findByDatasetAndName(dataset, branchName))
        .thenReturn(Optional.of(existingBranch));

    BranchResetEvent event = new BranchResetEvent(
        dataset,
        branchName,
        fromCommitIdStr,
        toCommitIdStr,
        Instant.now()
    );

    // When
    projector.handleBranchReset(event);

    // Then
    verify(branchRepository).updateBranchHead(
        eq(dataset),
        eq(branchName),
        eq(CommitId.of(toCommitIdStr))
    );
  }

  @Test
  void getProjectionState_shouldReturnCurrentState() {
    // Given
    String dataset = "test-dataset";
    String branchName = "main";
    CommitId commitId = CommitId.of("550e8400-e29b-41d4-a716-446655440000");
    Branch branch = new Branch(branchName, commitId);

    when(branchRepository.findByDatasetAndName(dataset, branchName))
        .thenReturn(Optional.of(branch));

    // When
    Optional<CommitId> result = projector.getProjectionState(dataset, branchName);

    // Then
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(commitId);
  }

  @Test
  void getProjectionState_shouldReturnEmptyWhenBranchNotFound() {
    // Given
    String dataset = "test-dataset";
    String branchName = "non-existent";

    when(branchRepository.findByDatasetAndName(dataset, branchName))
        .thenReturn(Optional.empty());

    // When
    Optional<CommitId> result = projector.getProjectionState(dataset, branchName);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  @SuppressWarnings("try")  // Suppress "resource never referenced" - used for MDC side-effects
  void handleEvent_shouldSkipDuplicateEvents_whenDeduplicationEnabled() {
    try (var ignored = ExpectedErrorContext.suppress("Skipping duplicate event")) {
      // Given
      ProjectorProperties.Deduplication dedup = new ProjectorProperties.Deduplication();
      dedup.setEnabled(true);
      dedup.setCacheSize(100);
      when(projectorProperties.getDeduplication()).thenReturn(dedup);

      // Recreate projector with deduplication enabled
      projector = new ReadModelProjector(branchRepository, commitRepository, tagRepository,
          materializedBranchRepo, datasetService, snapshotService, projectorProperties, meterRegistry);

      String dataset = "test-dataset";
      String commitIdStr = "550e8400-e29b-41d4-a716-446655440000";
      String rdfPatchStr = "TX .";
      Instant now = Instant.now();

      // Create two events with the SAME eventId
      CommitCreatedEvent event1 = new CommitCreatedEvent(
          "duplicate-event-id",  // Same event ID
          dataset,
          commitIdStr,
          List.of(),
          "main",
          "Test commit",
          "Test Author",
          now,
          rdfPatchStr
          , 1
      );

      CommitCreatedEvent event2 = new CommitCreatedEvent(
          "duplicate-event-id",  // Same event ID
          dataset,
          "different-commit-id",  // Different commit ID
          List.of(),
          "main",
          "Different commit",
          "Test Author",
          now,
          rdfPatchStr
          , 1
      );

      // When
      projector.handleEvent(toConsumerRecord(event1));  // First event should be processed
      projector.handleEvent(toConsumerRecord(event2));  // Second event should be skipped (duplicate)

      // Then - commit should only be saved once
      verify(commitRepository).save(eq(dataset), any(Commit.class), any(RDFPatch.class));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void handleEvent_shouldProcessAllEvents_whenDeduplicationDisabled() {
    // Given - deduplication already disabled in setUp()
    String dataset = "test-dataset";
    String commitId1 = "550e8400-e29b-41d4-a716-446655440000";
    String commitId2 = "660e8400-e29b-41d4-a716-446655440000";
    String rdfPatchStr = "TX .";
    Instant now = Instant.now();

    // Create two events with the SAME eventId
    CommitCreatedEvent event1 = new CommitCreatedEvent(
        "duplicate-event-id",  // Same event ID
        dataset,
        commitId1,
        List.of(),
        "main",
        "Test commit 1",
        "Test Author",
        now,
        rdfPatchStr
        , 1
    );

    CommitCreatedEvent event2 = new CommitCreatedEvent(
        "duplicate-event-id",  // Same event ID
        dataset,
        commitId2,
        List.of(),
        "main",
        "Test commit 2",
        "Test Author",
        now,
        rdfPatchStr
        , 1
    );

    // When
    projector.handleEvent(toConsumerRecord(event1));
    projector.handleEvent(toConsumerRecord(event2));

    // Then - both commits should be saved (no deduplication)
    verify(commitRepository, org.mockito.Mockito.times(2)).save(eq(dataset), any(Commit.class), any(RDFPatch.class));
  }

  @Test
  void handleEvent_shouldProcessDifferentEvents_whenDeduplicationEnabled() {
    // Given
    ProjectorProperties.Deduplication dedup = new ProjectorProperties.Deduplication();
    dedup.setEnabled(true);
    dedup.setCacheSize(100);
    when(projectorProperties.getDeduplication()).thenReturn(dedup);

    // Recreate projector with deduplication enabled
    projector = new ReadModelProjector(branchRepository, commitRepository, tagRepository,
        materializedBranchRepo, datasetService, snapshotService, projectorProperties, meterRegistry);

    String dataset = "test-dataset";
    String rdfPatchStr = "TX .";
    Instant now = Instant.now();

    // Create two events with DIFFERENT eventIds
    CommitCreatedEvent event1 = new CommitCreatedEvent(
        "event-id-1",  // Different event ID
        dataset,
        "550e8400-e29b-41d4-a716-446655440001",  // Valid UUID
        List.of(),
        "main",
        "Test commit 1",
        "Test Author",
        now,
        rdfPatchStr
        , 1
    );

    CommitCreatedEvent event2 = new CommitCreatedEvent(
        "event-id-2",  // Different event ID
        dataset,
        "550e8400-e29b-41d4-a716-446655440002",  // Valid UUID
        List.of(),
        "main",
        "Test commit 2",
        "Test Author",
        now,
        rdfPatchStr
        , 1
    );

    // When
    projector.handleEvent(toConsumerRecord(event1));
    projector.handleEvent(toConsumerRecord(event2));

    // Then - both commits should be saved (different event IDs)
    verify(commitRepository, org.mockito.Mockito.times(2)).save(eq(dataset), any(Commit.class), any(RDFPatch.class));
  }
}
