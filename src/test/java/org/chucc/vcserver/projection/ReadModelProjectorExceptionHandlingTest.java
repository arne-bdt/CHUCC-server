package org.chucc.vcserver.projection;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.chucc.vcserver.config.ProjectorProperties;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.projection.ReadModelProjector.ProjectionException;
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

/**
 * Unit tests for ReadModelProjector exception handling behavior.
 * Verifies that exceptions are properly propagated to trigger Kafka retry mechanisms.
 */
@ExtendWith(MockitoExtension.class)
class ReadModelProjectorExceptionHandlingTest {

  @Mock
  private CommitRepository commitRepository;

  @Mock
  private BranchRepository branchRepository;

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

  /**
   * Sets up the test fixtures before each test.
   */
  @BeforeEach
  void setUp() {
    // Configure deduplication properties
    ProjectorProperties.Deduplication dedup = new ProjectorProperties.Deduplication();
    dedup.setEnabled(false); // Disable for simpler testing
    when(projectorProperties.getDeduplication()).thenReturn(dedup);

    // Stub MeterRegistry to return mock Counter (lenient = optional stubbing)
    lenient().when(meterRegistry.counter(any(String.class), any(String[].class)))
        .thenReturn(mockCounter);

    projector = new ReadModelProjector(
        branchRepository,
        commitRepository,
        tagRepository,
        materializedBranchRepo,
        datasetService,
        snapshotService,
        projectorProperties,
        meterRegistry
    );
  }

  /**
   * Verifies that repository exceptions are properly wrapped and rethrown as ProjectionException.
   * This ensures Kafka's retry mechanism is triggered instead of silently failing.
   */
  @Test
  @SuppressWarnings("try")  // Suppress "resource never referenced" - used for MDC side-effects
  void handleEvent_whenRepositoryFails_shouldRethrowAsProjectionException() {
    try (var ignored = ExpectedErrorContext.suppress("Database connection failed")) {
      // Arrange: Create valid event
      CommitCreatedEvent event = new CommitCreatedEvent(
          "dataset1",
          "550e8400-e29b-41d4-a716-446655440000",
          List.of(),
          null, // no branch
          "Test commit",
          "author@example.com",
          Instant.now(),
          "TX ."
          , 1
      );

      // Mock repository to throw exception during save
      doThrow(new RuntimeException("Database connection failed"))
          .when(commitRepository).save(eq("dataset1"), any(), any());

      // Create mock ConsumerRecord
      ConsumerRecord<String, VersionControlEvent> record =
          new ConsumerRecord<>("vc-events-dataset1", 0, 0, "dataset1", event);

      // Act & Assert: Verify exception propagates
      assertThatThrownBy(() -> projector.handleEvent(record))
          .isInstanceOf(ProjectionException.class)
          .hasMessageContaining("Failed to project event")
          .hasCauseInstanceOf(RuntimeException.class);

      // Verify original exception is preserved in cause chain
      assertThatThrownBy(() -> projector.handleEvent(record))
          .isInstanceOf(ProjectionException.class)
          .cause()
          .hasMessageContaining("Database connection failed");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Verifies that malformed RDF patches cause projection failures that are properly reported.
   * Invalid RDF syntax should trigger retries, not silent failures.
   */
  @Test
  @SuppressWarnings("try")  // Suppress "resource never referenced" - used for MDC side-effects
  void handleEvent_whenMalformedRdfPatch_shouldRethrowAsProjectionException() {
    try (var ignored = ExpectedErrorContext.suppress("Code 'INVALID' not recognized")) {
      // Arrange: Event with invalid RDF patch syntax
      CommitCreatedEvent event = new CommitCreatedEvent(
          "dataset1",
          "550e8400-e29b-41d4-a716-446655440001",
          List.of(),
          null,
          "Test commit with bad patch",
          "author@example.com",
          Instant.now(),
          "INVALID RDF SYNTAX <<<"  // malformed RDF patch
          , 1
      );

      ConsumerRecord<String, VersionControlEvent> record =
          new ConsumerRecord<>("vc-events-dataset1", 0, 0, "dataset1", event);

      // Act & Assert: Verify exception propagates
      assertThatThrownBy(() -> projector.handleEvent(record))
          .isInstanceOf(ProjectionException.class)
          .hasMessageContaining("Failed to project event");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Verifies that null pointer exceptions during processing are wrapped and rethrown.
   * This tests the catch-all exception handler in ReadModelProjector.
   */
  @Test
  @SuppressWarnings("try")  // Suppress "resource never referenced" - used for MDC side-effects
  void handleEvent_whenNullPointerException_shouldRethrowAsProjectionException() {
    try (var ignored = ExpectedErrorContext.suppress("Unexpected null value")) {
      // Arrange: Create valid event
      CommitCreatedEvent event = new CommitCreatedEvent(
          "dataset1",
          "550e8400-e29b-41d4-a716-446655440002",
          List.of(),
          null,
          "Test commit",
          "author@example.com",
          Instant.now(),
          "TX ."
          , 1
      );

      // Mock repository to throw NPE (simulating unexpected error)
      doThrow(new NullPointerException("Unexpected null value"))
          .when(commitRepository).save(eq("dataset1"), any(), any());

      ConsumerRecord<String, VersionControlEvent> record =
          new ConsumerRecord<>("vc-events-dataset1", 0, 0, "dataset1", event);

      // Act & Assert: Verify exception propagates with correct type
      assertThatThrownBy(() -> projector.handleEvent(record))
          .isInstanceOf(ProjectionException.class)
          .hasMessageContaining("Failed to project event")
          .hasCauseInstanceOf(NullPointerException.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
