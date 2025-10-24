package org.chucc.vcserver.command;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.CherryPickedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.exception.CherryPickConflictException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CherryPickCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class CherryPickCommandHandlerTest {

  @Mock
  private EventPublisher eventPublisher;

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private CommitRepository commitRepository;

  private CherryPickCommandHandler handler;

  @BeforeEach
  void setUp() {
    // Mock EventPublisher.publish() to return completed future (lenient for tests that don't publish)
    lenient().when(eventPublisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));

    handler = new CherryPickCommandHandler(eventPublisher, branchRepository, commitRepository);
  }

  @Test
  void shouldProduceCherryPickedEvent() {
    // Given
    String targetBranchHeadId = "12345678-1234-1234-1234-111111111111";
    String sourceCommitId = "12345678-1234-1234-1234-222222222222";

    CherryPickCommand command = new CherryPickCommand(
        "test-dataset",
        sourceCommitId,
        "main",
        null,
        "alice");

    CommitId targetBranchHead = new CommitId(targetBranchHeadId);
    Branch targetBranch = new Branch("main", targetBranchHead);

    CommitId sourceId = new CommitId(sourceCommitId);
    Commit sourceCommit = new Commit(
        sourceId,
        List.of(),
        "bob",
        "Original commit",
        Instant.now(),
        0
    );

    RDFPatch sourcePatch = RDFPatchOps.emptyPatch();
    RDFPatch targetPatch = RDFPatchOps.emptyPatch();

    when(branchRepository.findByDatasetAndName("test-dataset", "main"))
        .thenReturn(Optional.of(targetBranch));
    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(sourceId)))
        .thenReturn(Optional.of(sourceCommit));
    when(commitRepository.findPatchByDatasetAndId(eq("test-dataset"), eq(sourceId)))
        .thenReturn(Optional.of(sourcePatch));
    when(commitRepository.findPatchByDatasetAndId(eq("test-dataset"), eq(targetBranchHead)))
        .thenReturn(Optional.of(targetPatch));

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertNotNull(event);
    assertEquals(CherryPickedEvent.class, event.getClass());

    CherryPickedEvent cherryPickEvent = (CherryPickedEvent) event;
    assertEquals("test-dataset", cherryPickEvent.dataset());
    assertNotNull(cherryPickEvent.newCommitId());
    assertEquals(sourceCommitId, cherryPickEvent.sourceCommitId());
    assertEquals("main", cherryPickEvent.branch());
    assertTrue(cherryPickEvent.message().contains(sourceCommitId));
    assertEquals("alice", cherryPickEvent.author());
    assertNotNull(cherryPickEvent.rdfPatch());
    assertNotNull(cherryPickEvent.timestamp());
  }

  @Test
  void shouldUseCustomMessageWhenProvided() {
    // Given
    String targetBranchHeadId = "12345678-1234-1234-1234-111111111111";
    String sourceCommitId = "12345678-1234-1234-1234-222222222222";

    CherryPickCommand command = new CherryPickCommand(
        "test-dataset",
        sourceCommitId,
        "main",
        "Custom cherry-pick message",
        "alice");

    CommitId targetBranchHead = new CommitId(targetBranchHeadId);
    Branch targetBranch = new Branch("main", targetBranchHead);

    CommitId sourceId = new CommitId(sourceCommitId);
    Commit sourceCommit = new Commit(
        sourceId,
        List.of(),
        "bob",
        "Original commit",
        Instant.now(),
        0
    );

    RDFPatch sourcePatch = RDFPatchOps.emptyPatch();
    RDFPatch targetPatch = RDFPatchOps.emptyPatch();

    when(branchRepository.findByDatasetAndName("test-dataset", "main"))
        .thenReturn(Optional.of(targetBranch));
    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(sourceId)))
        .thenReturn(Optional.of(sourceCommit));
    when(commitRepository.findPatchByDatasetAndId(eq("test-dataset"), eq(sourceId)))
        .thenReturn(Optional.of(sourcePatch));
    when(commitRepository.findPatchByDatasetAndId(eq("test-dataset"), eq(targetBranchHead)))
        .thenReturn(Optional.of(targetPatch));

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    CherryPickedEvent cherryPickEvent = (CherryPickedEvent) event;
    assertEquals("Custom cherry-pick message", cherryPickEvent.message());
  }

  @Test
  void shouldRejectIfTargetBranchDoesNotExist() {
    // Given
    String sourceCommitId = "12345678-1234-1234-1234-222222222222";

    CherryPickCommand command = new CherryPickCommand(
        "test-dataset",
        sourceCommitId,
        "nonexistent-branch",
        null,
        "alice");

    when(branchRepository.findByDatasetAndName("test-dataset", "nonexistent-branch"))
        .thenReturn(Optional.empty());

    // When/Then
    assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
  }

  @Test
  void shouldRejectIfSourceCommitDoesNotExist() {
    // Given
    String targetBranchHeadId = "12345678-1234-1234-1234-111111111111";
    String sourceCommitId = "12345678-1234-1234-1234-999999999999";

    CherryPickCommand command = new CherryPickCommand(
        "test-dataset",
        sourceCommitId,
        "main",
        null,
        "alice");

    CommitId targetBranchHead = new CommitId(targetBranchHeadId);
    Branch targetBranch = new Branch("main", targetBranchHead);

    when(branchRepository.findByDatasetAndName("test-dataset", "main"))
        .thenReturn(Optional.of(targetBranch));
    when(commitRepository.findByDatasetAndId(eq("test-dataset"), any(CommitId.class)))
        .thenReturn(Optional.empty());

    // When/Then
    assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
  }

  @Test
  void shouldRejectIfSourcePatchNotFound() {
    // Given
    String targetBranchHeadId = "12345678-1234-1234-1234-111111111111";
    String sourceCommitId = "12345678-1234-1234-1234-222222222222";

    CherryPickCommand command = new CherryPickCommand(
        "test-dataset",
        sourceCommitId,
        "main",
        null,
        "alice");

    CommitId targetBranchHead = new CommitId(targetBranchHeadId);
    Branch targetBranch = new Branch("main", targetBranchHead);

    CommitId sourceId = new CommitId(sourceCommitId);
    Commit sourceCommit = new Commit(
        sourceId,
        List.of(),
        "bob",
        "Original commit",
        Instant.now(),
        0
    );

    when(branchRepository.findByDatasetAndName("test-dataset", "main"))
        .thenReturn(Optional.of(targetBranch));
    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(sourceId)))
        .thenReturn(Optional.of(sourceCommit));
    when(commitRepository.findPatchByDatasetAndId(eq("test-dataset"), eq(sourceId)))
        .thenReturn(Optional.empty());

    // When/Then
    assertThrows(IllegalStateException.class, () -> handler.handle(command));
  }

  @Test
  void shouldDetectConflictWhenPatchesIntersect() {
    // Given
    String targetBranchHeadId = "12345678-1234-1234-1234-111111111111";
    String sourceCommitId = "12345678-1234-1234-1234-222222222222";

    CherryPickCommand command = new CherryPickCommand(
        "test-dataset",
        sourceCommitId,
        "main",
        null,
        "alice");

    CommitId targetBranchHead = new CommitId(targetBranchHeadId);
    Branch targetBranch = new Branch("main", targetBranchHead);

    CommitId sourceId = new CommitId(sourceCommitId);
    Commit sourceCommit = new Commit(
        sourceId,
        List.of(),
        "bob",
        "Original commit",
        Instant.now(),
        0
    );

    // Create patches that intersect (modify the same triple)
    Node g = NodeFactory.createURI("http://example.org/g");
    Node s = NodeFactory.createURI("http://example.org/s");
    Node p = NodeFactory.createURI("http://example.org/p");
    Node o = NodeFactory.createLiteralString("value");

    RDFChangesCollector sourceCollector = new RDFChangesCollector();
    sourceCollector.start();
    sourceCollector.add(g, s, p, o);
    sourceCollector.finish();
    RDFPatch sourcePatch = sourceCollector.getRDFPatch();

    RDFChangesCollector targetCollector = new RDFChangesCollector();
    targetCollector.start();
    targetCollector.delete(g, s, p, o);
    targetCollector.finish();
    RDFPatch targetPatch = targetCollector.getRDFPatch();

    when(branchRepository.findByDatasetAndName("test-dataset", "main"))
        .thenReturn(Optional.of(targetBranch));
    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(sourceId)))
        .thenReturn(Optional.of(sourceCommit));
    when(commitRepository.findPatchByDatasetAndId(eq("test-dataset"), eq(sourceId)))
        .thenReturn(Optional.of(sourcePatch));
    when(commitRepository.findPatchByDatasetAndId(eq("test-dataset"), eq(targetBranchHead)))
        .thenReturn(Optional.of(targetPatch));

    // When/Then
    assertThrows(CherryPickConflictException.class, () -> handler.handle(command));
  }
}
