package org.chucc.vcserver.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
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
import org.chucc.vcserver.exception.PreconditionFailedException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.service.ConflictDetectionService;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.GraphDiffService;
import org.chucc.vcserver.service.PreconditionService;
import org.chucc.vcserver.service.RdfParsingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for PutGraphCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class PutGraphCommandHandlerTest {

  @Mock
  private EventPublisher eventPublisher;

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private DatasetService datasetService;

  @Mock
  private RdfParsingService rdfParsingService;

  @Mock
  private GraphDiffService graphDiffService;

  @Mock
  private PreconditionService preconditionService;

  @Mock
  private ConflictDetectionService conflictDetectionService;

  private PutGraphCommandHandler handler;

  @BeforeEach
  void setUp() {
    // Mock EventPublisher.publish() to return completed future (lenient for tests that don't publish)
    lenient().when(eventPublisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));

    handler = new PutGraphCommandHandler(
        eventPublisher,
        branchRepository,
        datasetService,
        rdfParsingService,
        graphDiffService,
        preconditionService,
        conflictDetectionService
    );
  }

  @Test
  void handle_shouldCreateCommit_whenGraphIsReplaced() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model oldGraph = ModelFactory.createDefaultModel();
    oldGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "oldValue"
    );

    Model newGraph = ModelFactory.createDefaultModel();
    newGraph.add(
        ResourceFactory.createResource("http://example.org/s2"),
        ResourceFactory.createProperty("http://example.org/p2"),
        "newValue"
    );

    RDFPatch patch = RDFPatchOps.emptyPatch(); // Mock patch

    PutGraphCommand command = new PutGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        "@prefix ex: <http://example.org/> . ex:s2 ex:p2 \"newValue\" .",
        "text/turtle",
        "testAuthor",
        "Replace graph",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph("default", baseCommit, "http://example.org/graph1"))
        .thenReturn(oldGraph);
    when(rdfParsingService.parseRdf(anyString(), eq("text/turtle")))
        .thenReturn(newGraph.getGraph());
    when(graphDiffService.computePutDiff(oldGraph, newGraph, "http://example.org/graph1"))
        .thenReturn(patch);
    when(graphDiffService.isPatchEmpty(patch)).thenReturn(false);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isInstanceOf(CommitCreatedEvent.class);
    CommitCreatedEvent commitEvent = (CommitCreatedEvent) event;
    assertThat(commitEvent.dataset()).isEqualTo("default");
    assertThat(commitEvent.author()).isEqualTo("testAuthor");
    assertThat(commitEvent.message()).isEqualTo("Replace graph");
    assertThat(commitEvent.parents()).contains(baseCommit.value());

  }

  @Test
  void handle_shouldReturnNull_whenPatchIsEmpty() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model oldGraph = ModelFactory.createDefaultModel();
    oldGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

    Model newGraph = ModelFactory.createDefaultModel();
    newGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

    RDFPatch emptyPatch = RDFPatchOps.emptyPatch();

    PutGraphCommand command = new PutGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \"value\" .",
        "text/turtle",
        "testAuthor",
        "No-op replace",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph("default", baseCommit, "http://example.org/graph1"))
        .thenReturn(oldGraph);
    when(rdfParsingService.parseRdf(anyString(), eq("text/turtle")))
        .thenReturn(newGraph.getGraph());
    when(graphDiffService.computePutDiff(oldGraph, newGraph, "http://example.org/graph1"))
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

    Model oldGraph = ModelFactory.createDefaultModel();
    Model newGraph = ModelFactory.createDefaultModel();
    newGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

    RDFPatch patch = RDFPatchOps.emptyPatch();

    PutGraphCommand command = new PutGraphCommand(
        "default",
        null,
        true,
        "main",
        baseCommit,
        "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \"value\" .",
        "text/turtle",
        "testAuthor",
        "Replace default graph",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getDefaultGraph("default", baseCommit))
        .thenReturn(oldGraph);
    when(rdfParsingService.parseRdf(anyString(), eq("text/turtle")))
        .thenReturn(newGraph.getGraph());
    when(graphDiffService.computePutDiff(oldGraph, newGraph, null))
        .thenReturn(patch);
    when(graphDiffService.isPatchEmpty(patch)).thenReturn(false);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isNotNull();
    verify(datasetService).getDefaultGraph("default", baseCommit);
    verify(graphDiffService).computePutDiff(oldGraph, newGraph, null);
  }

  @Test
  void handle_shouldCheckPrecondition_whenIfMatchProvided() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model oldGraph = ModelFactory.createDefaultModel();
    Model newGraph = ModelFactory.createDefaultModel();
    RDFPatch patch = RDFPatchOps.emptyPatch();

    PutGraphCommand command = new PutGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        "",
        "text/turtle",
        "testAuthor",
        "Replace graph",
        "\"" + baseCommit.value() + "\""
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph(anyString(), any(), anyString()))
        .thenReturn(oldGraph);
    when(rdfParsingService.parseRdf(anyString(), anyString()))
        .thenReturn(newGraph.getGraph());
    when(graphDiffService.computePutDiff(any(), any(), anyString()))
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

    PutGraphCommand command = new PutGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        "",
        "text/turtle",
        "testAuthor",
        "Replace graph",
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
    CommitId baseCommit = CommitId.generate(); // Different from currentHead
    Branch branch = new Branch("main", currentHead);

    Model oldGraph = ModelFactory.createDefaultModel();
    Model newGraph = ModelFactory.createDefaultModel();
    newGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

    RDFPatch patch = RDFPatchOps.emptyPatch();

    PutGraphCommand command = new PutGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \"value\" .",
        "text/turtle",
        "testAuthor",
        "Replace graph",
        "\"" + currentHead.value() + "\"" // If-Match matches currentHead (precondition passes)
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    // Precondition passes (If-Match == currentHead)
    when(datasetService.getGraph("default", baseCommit, "http://example.org/graph1"))
        .thenReturn(oldGraph);
    when(rdfParsingService.parseRdf(anyString(), eq("text/turtle")))
        .thenReturn(newGraph.getGraph());
    when(graphDiffService.computePutDiff(oldGraph, newGraph, "http://example.org/graph1"))
        .thenReturn(patch);
    when(graphDiffService.isPatchEmpty(patch)).thenReturn(false);

    // Conflict detection throws 409 because baseCommit != currentHead with overlapping changes
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

    PutGraphCommand command = new PutGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "nonexistent",
        baseCommit,
        "",
        "text/turtle",
        "testAuthor",
        "Replace graph",
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
  void handle_shouldHandleNonExistentGraph_whenCreatingNewGraph() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model newGraph = ModelFactory.createDefaultModel();
    newGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

    RDFPatch patch = RDFPatchOps.emptyPatch();

    PutGraphCommand command = new PutGraphCommand(
        "default",
        "http://example.org/newGraph",
        false,
        "main",
        baseCommit,
        "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \"value\" .",
        "text/turtle",
        "testAuthor",
        "Create new graph",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph("default", baseCommit, "http://example.org/newGraph"))
        .thenReturn(null); // Graph doesn't exist
    when(rdfParsingService.parseRdf(anyString(), eq("text/turtle")))
        .thenReturn(newGraph.getGraph());
    when(graphDiffService.computePutDiff(any(Model.class), eq(newGraph),
            eq("http://example.org/newGraph")))
        .thenReturn(patch);
    when(graphDiffService.isPatchEmpty(patch)).thenReturn(false);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isNotNull();
    // Should compute diff with empty model when graph doesn't exist
    verify(graphDiffService).computePutDiff(any(Model.class), eq(newGraph),
        eq("http://example.org/newGraph"));
  }
}
