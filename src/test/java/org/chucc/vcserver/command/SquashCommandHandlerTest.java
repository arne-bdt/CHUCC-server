package org.chucc.vcserver.command;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.mem.DatasetGraphInMemory;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.CommitsSquashedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.service.DatasetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SquashCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class SquashCommandHandlerTest {

  @Mock
  private EventPublisher eventPublisher;

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private CommitRepository commitRepository;

  @Mock
  private DatasetService datasetService;

  private SquashCommandHandler handler;

  @BeforeEach
  void setUp() {
    // Mock EventPublisher.publish() to return completed future (lenient for tests that don't publish)
    lenient().when(eventPublisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));

    handler = new SquashCommandHandler(
        eventPublisher, branchRepository, commitRepository, datasetService);
  }

  @Test
  void shouldSquashTwoCommits() {
    // Given: Branch has commits A -> B -> C
    //        Squash B and C into one commit
    //        Expected: Branch has A -> BC' (where BC' combines B and C)

    String commitAId = "12345678-1234-1234-1234-111111111111";
    String commitBId = "12345678-1234-1234-1234-222222222222";
    String commitCId = "12345678-1234-1234-1234-333333333333";

    CommitId idA = new CommitId(commitAId);
    CommitId idB = new CommitId(commitBId);
    CommitId idC = new CommitId(commitCId);

    Commit commitA = new Commit(idA, List.of(), "alice", "Commit A", Instant.now());
    Commit commitB = new Commit(idB, List.of(idA), "bob", "Commit B", Instant.now());
    Commit commitC = new Commit(idC, List.of(idB), "bob", "Commit C", Instant.now());

    Branch branch = new Branch("feature", idC);

    // Mock repository calls
    when(branchRepository.findByDatasetAndName("test-dataset", "feature"))
        .thenReturn(Optional.of(branch));

    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(idB)))
        .thenReturn(Optional.of(commitB));
    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(idC)))
        .thenReturn(Optional.of(commitC));

    // Mock dataset states for diff
    DatasetGraph beforeState = new DatasetGraphInMemory();
    DatasetGraph afterState = new DatasetGraphInMemory();
    // Add a triple to afterState to simulate combined changes
    afterState.getDefaultGraph().add(
        NodeFactory.createURI("http://example.org/s"),
        NodeFactory.createURI("http://example.org/p"),
        NodeFactory.createLiteralString("combined-value")
    );

    when(datasetService.materializeCommit("test-dataset", idA))
        .thenReturn(beforeState);
    when(datasetService.materializeCommit("test-dataset", idC))
        .thenReturn(afterState);

    SquashCommand command = new SquashCommand(
        "test-dataset",
        "feature",
        List.of(commitBId, commitCId),
        "Squashed B and C",
        "alice");

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertNotNull(event);
    assertEquals(CommitsSquashedEvent.class, event.getClass());

    CommitsSquashedEvent squashEvent = (CommitsSquashedEvent) event;
    assertEquals("test-dataset", squashEvent.dataset());
    assertEquals("feature", squashEvent.branch());
    assertNotNull(squashEvent.newCommitId());
    assertEquals(2, squashEvent.squashedCommitIds().size());
    assertEquals(commitBId, squashEvent.squashedCommitIds().get(0));
    assertEquals(commitCId, squashEvent.squashedCommitIds().get(1));
    assertEquals("alice", squashEvent.author());
    assertEquals("Squashed B and C", squashEvent.message());
    assertNotNull(squashEvent.timestamp());
    assertEquals(commitCId, squashEvent.previousHead());

    // Verify branch was updated
    ArgumentCaptor<Branch> branchCaptor = ArgumentCaptor.forClass(Branch.class);
    verify(branchRepository).save(eq("test-dataset"), branchCaptor.capture());
    assertNotEquals(idC, branchCaptor.getValue().getCommitId());

    // Verify new commit was saved
    verify(commitRepository).save(eq("test-dataset"), any(Commit.class), any(RDFPatch.class));
  }

  @Test
  void shouldSquashThreeCommits() {
    // Given: Branch has commits A -> B -> C -> D
    //        Squash B, C, and D
    //        Expected: Branch has A -> BCD'

    String commitAId = "12345678-1234-1234-1234-111111111111";
    String commitBId = "12345678-1234-1234-1234-222222222222";
    String commitCId = "12345678-1234-1234-1234-333333333333";
    String commitDId = "12345678-1234-1234-1234-444444444444";

    CommitId idA = new CommitId(commitAId);
    CommitId idB = new CommitId(commitBId);
    CommitId idC = new CommitId(commitCId);
    CommitId idD = new CommitId(commitDId);

    Commit commitA = new Commit(idA, List.of(), "alice", "Commit A", Instant.now());
    Commit commitB = new Commit(idB, List.of(idA), "bob", "Commit B", Instant.now());
    Commit commitC = new Commit(idC, List.of(idB), "bob", "Commit C", Instant.now());
    Commit commitD = new Commit(idD, List.of(idC), "bob", "Commit D", Instant.now());

    Branch branch = new Branch("feature", idD);

    when(branchRepository.findByDatasetAndName("test-dataset", "feature"))
        .thenReturn(Optional.of(branch));

    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(idB)))
        .thenReturn(Optional.of(commitB));
    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(idC)))
        .thenReturn(Optional.of(commitC));
    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(idD)))
        .thenReturn(Optional.of(commitD));

    DatasetGraph beforeState = new DatasetGraphInMemory();
    DatasetGraph afterState = new DatasetGraphInMemory();

    when(datasetService.materializeCommit("test-dataset", idA))
        .thenReturn(beforeState);
    when(datasetService.materializeCommit("test-dataset", idD))
        .thenReturn(afterState);

    SquashCommand command = new SquashCommand(
        "test-dataset",
        "feature",
        List.of(commitBId, commitCId, commitDId),
        "Squashed B, C, and D",
        null);  // null author should use first commit's author

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    CommitsSquashedEvent squashEvent = (CommitsSquashedEvent) event;
    assertEquals(3, squashEvent.squashedCommitIds().size());
    assertEquals("bob", squashEvent.author());  // Should use first commit's author
  }

  @Test
  void shouldRejectIfBranchDoesNotExist() {
    // Given
    SquashCommand command = new SquashCommand(
        "test-dataset",
        "nonexistent-branch",
        List.of("commit1", "commit2"),
        "Squash message",
        "alice");

    when(branchRepository.findByDatasetAndName("test-dataset", "nonexistent-branch"))
        .thenReturn(Optional.empty());

    // When/Then
    assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
  }

  @Test
  void shouldRejectIfCommitDoesNotExist() {
    // Given
    String commitBId = "12345678-1234-1234-1234-222222222222";
    String commitCId = "12345678-1234-1234-1234-333333333333";

    CommitId idC = new CommitId(commitCId);
    Branch branch = new Branch("feature", idC);

    when(branchRepository.findByDatasetAndName("test-dataset", "feature"))
        .thenReturn(Optional.of(branch));

    when(commitRepository.findByDatasetAndId(eq("test-dataset"), any(CommitId.class)))
        .thenReturn(Optional.empty());

    SquashCommand command = new SquashCommand(
        "test-dataset",
        "feature",
        List.of(commitBId, commitCId),
        "Squash message",
        "alice");

    // When/Then
    assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
  }

  @Test
  void shouldRejectIfCommitsNotContiguous() {
    // Given: Branch has A -> B -> C -> D
    //        Try to squash B and D (skip C)

    String commitAId = "12345678-1234-1234-1234-111111111111";
    String commitBId = "12345678-1234-1234-1234-222222222222";
    String commitCId = "12345678-1234-1234-1234-333333333333";
    String commitDId = "12345678-1234-1234-1234-444444444444";

    CommitId idA = new CommitId(commitAId);
    CommitId idB = new CommitId(commitBId);
    CommitId idC = new CommitId(commitCId);
    CommitId idD = new CommitId(commitDId);

    Commit commitA = new Commit(idA, List.of(), "alice", "Commit A", Instant.now());
    Commit commitB = new Commit(idB, List.of(idA), "bob", "Commit B", Instant.now());
    Commit commitC = new Commit(idC, List.of(idB), "bob", "Commit C", Instant.now());
    Commit commitD = new Commit(idD, List.of(idC), "bob", "Commit D", Instant.now());

    Branch branch = new Branch("feature", idD);

    when(branchRepository.findByDatasetAndName("test-dataset", "feature"))
        .thenReturn(Optional.of(branch));

    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(idB)))
        .thenReturn(Optional.of(commitB));
    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(idD)))
        .thenReturn(Optional.of(commitD));

    SquashCommand command = new SquashCommand(
        "test-dataset",
        "feature",
        List.of(commitBId, commitDId),  // B -> D (skips C, not contiguous)
        "Squash message",
        "alice");

    // When/Then
    assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
  }

  @Test
  void shouldRejectIfSquashingInitialCommit() {
    // Given: Try to squash commit A which has no parent
    String commitAId = "12345678-1234-1234-1234-111111111111";
    String commitBId = "12345678-1234-1234-1234-222222222222";

    CommitId idA = new CommitId(commitAId);
    CommitId idB = new CommitId(commitBId);

    Commit commitA = new Commit(idA, List.of(), "alice", "Commit A", Instant.now());
    Commit commitB = new Commit(idB, List.of(idA), "bob", "Commit B", Instant.now());

    Branch branch = new Branch("feature", idB);

    when(branchRepository.findByDatasetAndName("test-dataset", "feature"))
        .thenReturn(Optional.of(branch));

    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(idA)))
        .thenReturn(Optional.of(commitA));
    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(idB)))
        .thenReturn(Optional.of(commitB));

    SquashCommand command = new SquashCommand(
        "test-dataset",
        "feature",
        List.of(commitAId, commitBId),
        "Squash message",
        "alice");

    // When/Then
    assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
  }
}
