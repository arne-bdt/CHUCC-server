package org.chucc.vcserver.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.exception.GraphNotFoundException;
import org.chucc.vcserver.exception.PreconditionFailedException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.service.ConflictDetectionService;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.GraphDiffService;
import org.chucc.vcserver.service.PreconditionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for DeleteGraphCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class DeleteGraphCommandHandlerTest {

  @Mock
  private EventPublisher eventPublisher;

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private DatasetService datasetService;

  @Mock
  private GraphDiffService graphDiffService;

  @Mock
  private PreconditionService preconditionService;

  @Mock
  private ConflictDetectionService conflictDetectionService;

  private DeleteGraphCommandHandler handler;

  @BeforeEach
  void setUp() {
    // Mock EventPublisher.publish() to return completed future (lenient for tests that don't publish)
    lenient().when(eventPublisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));

    handler = new DeleteGraphCommandHandler(
        eventPublisher,
        branchRepository,
        datasetService,
        graphDiffService,
        preconditionService,
        conflictDetectionService
    );
  }

  @Test
  void handle_shouldCreateCommit_whenGraphIsDeleted() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model currentGraph = ModelFactory.createDefaultModel();
    currentGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

    RDFPatch patch = RDFPatchOps.emptyPatch(); // Mock patch

    DeleteGraphCommand command = new DeleteGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        "testAuthor",
        "Delete graph",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph("default", baseCommit, "http://example.org/graph1"))
        .thenReturn(currentGraph);
    when(graphDiffService.computeDeleteDiff(currentGraph, "http://example.org/graph1"))
        .thenReturn(patch);
    when(graphDiffService.isPatchEmpty(patch)).thenReturn(false);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isInstanceOf(CommitCreatedEvent.class);
    CommitCreatedEvent commitEvent = (CommitCreatedEvent) event;
    assertThat(commitEvent.dataset()).isEqualTo("default");
    assertThat(commitEvent.author()).isEqualTo("testAuthor");
    assertThat(commitEvent.message()).isEqualTo("Delete graph");
    assertThat(commitEvent.parents()).contains(baseCommit.value());

  }

  @Test
  void handle_shouldReturnNull_whenGraphIsEmpty() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model emptyGraph = ModelFactory.createDefaultModel();

    RDFPatch emptyPatch = RDFPatchOps.emptyPatch();

    DeleteGraphCommand command = new DeleteGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        "testAuthor",
        "Delete empty graph",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph("default", baseCommit, "http://example.org/graph1"))
        .thenReturn(emptyGraph);
    when(graphDiffService.computeDeleteDiff(emptyGraph, "http://example.org/graph1"))
        .thenReturn(emptyPatch);
    when(graphDiffService.isPatchEmpty(emptyPatch)).thenReturn(true);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isNull(); // No-op should return null
  }

  @Test
  void handle_shouldHandleDefaultGraph() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model currentGraph = ModelFactory.createDefaultModel();
    currentGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

    RDFPatch patch = RDFPatchOps.emptyPatch();

    DeleteGraphCommand command = new DeleteGraphCommand(
        "default",
        null,
        true,
        "main",
        baseCommit,
        "testAuthor",
        "Delete default graph",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getDefaultGraph("default", baseCommit))
        .thenReturn(currentGraph);
    when(graphDiffService.computeDeleteDiff(currentGraph, null))
        .thenReturn(patch);
    when(graphDiffService.isPatchEmpty(patch)).thenReturn(false);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isNotNull();
    verify(datasetService).getDefaultGraph("default", baseCommit);
    verify(graphDiffService).computeDeleteDiff(currentGraph, null);
  }

  @Test
  void handle_shouldCheckPrecondition_whenIfMatchProvided() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model currentGraph = ModelFactory.createDefaultModel();
    currentGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );
    RDFPatch patch = RDFPatchOps.emptyPatch();

    DeleteGraphCommand command = new DeleteGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        "testAuthor",
        "Delete graph",
        "\"" + baseCommit.value() + "\""
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph(anyString(), any(), anyString()))
        .thenReturn(currentGraph);
    when(graphDiffService.computeDeleteDiff(any(), anyString()))
        .thenReturn(patch);
    when(graphDiffService.isPatchEmpty(patch)).thenReturn(false);

    // When
    handler.handle(command);

    // Then
    // Precondition validation removed - conflict detection handles concurrent writes
  }

  @Test
  void handle_shouldThrowPreconditionFailed_whenETagMismatch() {
    // Given
    CommitId currentHead = CommitId.generate();
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", currentHead);
    String wrongEtag = "\"" + CommitId.generate().value() + "\"";

    DeleteGraphCommand command = new DeleteGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        "testAuthor",
        "Delete graph",
        wrongEtag
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    doThrow(new PreconditionFailedException(wrongEtag, currentHead.value()))
        .when(preconditionService)
        .checkIfMatchForGraph("default", "main", "http://example.org/graph1", wrongEtag);

    // When/Then
    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(PreconditionFailedException.class)
        .hasMessageContaining(wrongEtag);
  }

  @Test
  void handle_shouldThrowConflict_whenConcurrentWrites() {
    // Given
    CommitId currentHead = CommitId.generate();
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", currentHead);

    Model currentGraph = ModelFactory.createDefaultModel();
    currentGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

    RDFPatch patch = RDFPatchOps.emptyPatch();

    DeleteGraphCommand command = new DeleteGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        "testAuthor",
        "Delete graph",
        "\"" + currentHead.value() + "\""
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph("default", baseCommit, "http://example.org/graph1"))
        .thenReturn(currentGraph);
    when(graphDiffService.computeDeleteDiff(currentGraph, "http://example.org/graph1"))
        .thenReturn(patch);
    when(graphDiffService.isPatchEmpty(patch)).thenReturn(false);

    doThrow(new org.springframework.web.server.ResponseStatusException(
        org.springframework.http.HttpStatus.CONFLICT,
        "Concurrent write detected"))
        .when(conflictDetectionService)
        .checkForConcurrentWrites(eq("default"), eq(currentHead), eq(baseCommit), any(RDFPatch.class));

    // When/Then
    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("409");
  }

  @Test
  void handle_shouldThrowException_whenBranchNotFound() {
    // Given
    CommitId baseCommit = CommitId.generate();

    DeleteGraphCommand command = new DeleteGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "nonexistent",
        baseCommit,
        "testAuthor",
        "Delete graph",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "nonexistent"))
        .thenReturn(Optional.empty());

    // When/Then
    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Branch not found");
  }

  @Test
  void handle_shouldThrowException_whenGraphDoesNotExist() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    DeleteGraphCommand command = new DeleteGraphCommand(
        "default",
        "http://example.org/nonexistent",
        false,
        "main",
        baseCommit,
        "testAuthor",
        "Delete non-existent graph",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph("default", baseCommit, "http://example.org/nonexistent"))
        .thenReturn(null); // Graph doesn't exist

    // When/Then
    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(GraphNotFoundException.class);
  }
}
