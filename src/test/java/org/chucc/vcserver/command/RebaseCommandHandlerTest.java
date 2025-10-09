package org.chucc.vcserver.command;

import java.time.Instant;
import java.util.ArrayList;
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
import org.chucc.vcserver.event.BranchRebasedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.exception.RebaseConflictException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.repository.TagRepository;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RebaseCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class RebaseCommandHandlerTest {

  @Mock
  private EventPublisher eventPublisher;

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private CommitRepository commitRepository;

  @Mock
  private TagRepository tagRepository;

  private RebaseCommandHandler handler;

  @BeforeEach
  void setUp() {
    // Mock EventPublisher.publish() to return completed future (lenient for tests that don't publish)
    lenient().when(eventPublisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));

    handler = new RebaseCommandHandler(
        eventPublisher, branchRepository, commitRepository, tagRepository);
  }

  @Test
  void shouldProduceBranchRebasedEvent() {
    // Given: main has commits A -> B -> C
    //        feature has commits A -> D
    //        Rebase feature onto C (from A)
    //        Expected result: feature has C -> D'

    String commitAId = "12345678-1234-1234-1234-111111111111";
    String commitBId = "12345678-1234-1234-1234-222222222222";
    String commitCId = "12345678-1234-1234-1234-333333333333";
    String commitDId = "12345678-1234-1234-1234-444444444444";

    CommitId idA = new CommitId(commitAId);
    CommitId idB = new CommitId(commitBId);
    CommitId idC = new CommitId(commitCId);
    CommitId idD = new CommitId(commitDId);

    // Create commits
    Commit commitA = new Commit(idA, List.of(), "alice", "Commit A", Instant.now());
    Commit commitB = new Commit(idB, List.of(idA), "alice", "Commit B", Instant.now());
    Commit commitC = new Commit(idC, List.of(idB), "alice", "Commit C", Instant.now());
    Commit commitD = new Commit(idD, List.of(idA), "bob", "Commit D", Instant.now());

    // Create patches (non-conflicting)
    RDFPatch patchD = createPatch("http://example.org/d", "value-d");

    // Setup branches
    Branch featureBranch = new Branch("feature", idD);

    // Mock repository calls
    when(branchRepository.findByDatasetAndName("test-dataset", "feature"))
        .thenReturn(Optional.of(featureBranch));

    // Mock commit lookups
    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(idC)))
        .thenReturn(Optional.of(commitC));
    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(idD)))
        .thenReturn(Optional.of(commitD));
    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(idA)))
        .thenReturn(Optional.of(commitA));

    // Mock patch lookups
    when(commitRepository.findPatchByDatasetAndId(eq("test-dataset"), eq(idD)))
        .thenReturn(Optional.of(patchD));

    RebaseCommand command = new RebaseCommand(
        "test-dataset",
        "feature",
        commitCId,
        commitAId,
        "alice");

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertNotNull(event);
    assertEquals(BranchRebasedEvent.class, event.getClass());

    BranchRebasedEvent rebaseEvent = (BranchRebasedEvent) event;
    assertEquals("test-dataset", rebaseEvent.dataset());
    assertEquals("feature", rebaseEvent.branch());
    assertEquals(commitDId, rebaseEvent.previousHead());
    assertNotNull(rebaseEvent.newHead());
    assertEquals(1, rebaseEvent.newCommits().size());
    assertEquals("alice", rebaseEvent.author());
    assertNotNull(rebaseEvent.timestamp());

    // Verify branch was updated
    verify(branchRepository).save(eq("test-dataset"), any(Branch.class));

    // Verify new commit was saved
    verify(commitRepository).save(eq("test-dataset"), any(Commit.class), any(RDFPatch.class));
  }

  @Test
  void shouldRejectIfBranchDoesNotExist() {
    // Given
    String commitCId = "12345678-1234-1234-1234-333333333333";
    String commitAId = "12345678-1234-1234-1234-111111111111";

    RebaseCommand command = new RebaseCommand(
        "test-dataset",
        "nonexistent-branch",
        commitCId,
        commitAId,
        "alice");

    when(branchRepository.findByDatasetAndName("test-dataset", "nonexistent-branch"))
        .thenReturn(Optional.empty());

    // When/Then
    assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
  }

  @Test
  void shouldRejectIfOntoCommitDoesNotExist() {
    // Given
    String commitCId = "12345678-1234-1234-1234-333333333333";
    String commitAId = "12345678-1234-1234-1234-111111111111";
    String commitDId = "12345678-1234-1234-1234-444444444444";

    CommitId idD = new CommitId(commitDId);
    Branch featureBranch = new Branch("feature", idD);

    RebaseCommand command = new RebaseCommand(
        "test-dataset",
        "feature",
        commitCId,
        commitAId,
        "alice");

    when(branchRepository.findByDatasetAndName("test-dataset", "feature"))
        .thenReturn(Optional.of(featureBranch));

    when(commitRepository.findByDatasetAndId(eq("test-dataset"), any(CommitId.class)))
        .thenReturn(Optional.empty());

    // When/Then
    assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
  }

  @Test
  void shouldRejectIfNoCommitsToRebase() {
    // Given: Branch already at target commit
    String commitCId = "12345678-1234-1234-1234-333333333333";

    CommitId idC = new CommitId(commitCId);
    Commit commitC = new Commit(idC, List.of(), "alice", "Commit C", Instant.now());

    Branch featureBranch = new Branch("feature", idC);

    RebaseCommand command = new RebaseCommand(
        "test-dataset",
        "feature",
        commitCId,
        commitCId,  // from and onto are the same
        "alice");

    when(branchRepository.findByDatasetAndName("test-dataset", "feature"))
        .thenReturn(Optional.of(featureBranch));

    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(idC)))
        .thenReturn(Optional.of(commitC));

    // When/Then
    assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
  }

  @Test
  void shouldRebaseMultipleCommits() {
    // Given: main has A -> B -> C
    //        feature has A -> D -> E
    //        Rebase feature onto C (from A)
    //        Expected: feature has C -> D' -> E'

    String commitAId = "12345678-1234-1234-1234-111111111111";
    String commitCId = "12345678-1234-1234-1234-333333333333";
    String commitDId = "12345678-1234-1234-1234-444444444444";
    String commitEId = "12345678-1234-1234-1234-555555555555";

    CommitId idA = new CommitId(commitAId);
    CommitId idC = new CommitId(commitCId);
    CommitId idD = new CommitId(commitDId);
    CommitId idE = new CommitId(commitEId);

    Commit commitA = new Commit(idA, List.of(), "alice", "Commit A", Instant.now());
    Commit commitC = new Commit(idC, List.of(), "alice", "Commit C", Instant.now());
    Commit commitD = new Commit(idD, List.of(idA), "bob", "Commit D", Instant.now());
    Commit commitE = new Commit(idE, List.of(idD), "bob", "Commit E", Instant.now());

    RDFPatch patchD = createPatch("http://example.org/d", "value-d");
    RDFPatch patchE = createPatch("http://example.org/e", "value-e");

    Branch featureBranch = new Branch("feature", idE);

    when(branchRepository.findByDatasetAndName("test-dataset", "feature"))
        .thenReturn(Optional.of(featureBranch));

    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(idC)))
        .thenReturn(Optional.of(commitC));
    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(idE)))
        .thenReturn(Optional.of(commitE));
    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(idD)))
        .thenReturn(Optional.of(commitD));
    when(commitRepository.findByDatasetAndId(eq("test-dataset"), eq(idA)))
        .thenReturn(Optional.of(commitA));

    when(commitRepository.findPatchByDatasetAndId(eq("test-dataset"), eq(idD)))
        .thenReturn(Optional.of(patchD));
    when(commitRepository.findPatchByDatasetAndId(eq("test-dataset"), eq(idE)))
        .thenReturn(Optional.of(patchE));

    RebaseCommand command = new RebaseCommand(
        "test-dataset",
        "feature",
        commitCId,
        commitAId,
        "alice");

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    BranchRebasedEvent rebaseEvent = (BranchRebasedEvent) event;
    assertEquals(2, rebaseEvent.newCommits().size());

    // Verify 2 new commits were saved
    verify(commitRepository, times(2))
        .save(eq("test-dataset"), any(Commit.class), any(RDFPatch.class));
  }

  /**
   * Helper method to create a simple RDF patch.
   *
   * @param subject the subject URI
   * @param value the literal value
   * @return an RDF patch
   */
  private RDFPatch createPatch(String subject, String value) {
    Node g = NodeFactory.createURI("http://example.org/g");
    Node s = NodeFactory.createURI(subject);
    Node p = NodeFactory.createURI("http://example.org/p");
    Node o = NodeFactory.createLiteralString(value);

    RDFChangesCollector collector = new RDFChangesCollector();
    collector.start();
    collector.add(g, s, p, o);
    collector.finish();
    return collector.getRDFPatch();
  }
}
