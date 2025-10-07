package org.chucc.vcserver.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.exception.PreconditionFailedException;
import org.chucc.vcserver.repository.BranchRepository;
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
 * Unit tests for PostGraphCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class PostGraphCommandHandlerTest {

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

  private PostGraphCommandHandler handler;

  @BeforeEach
  void setUp() {
    handler = new PostGraphCommandHandler(
        branchRepository,
        datasetService,
        rdfParsingService,
        graphDiffService,
        preconditionService
    );
  }

  @Test
  void handle_shouldCreateCommit_whenNewTriplesAdded() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model currentGraph = ModelFactory.createDefaultModel();
    currentGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "existingValue"
    );

    Model newContent = ModelFactory.createDefaultModel();
    newContent.add(
        ResourceFactory.createResource("http://example.org/s2"),
        ResourceFactory.createProperty("http://example.org/p2"),
        "newValue"
    );

    RDFPatch patch = RDFPatchOps.emptyPatch(); // Mock patch

    PostGraphCommand command = new PostGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        "@prefix ex: <http://example.org/> . ex:s2 ex:p2 \"newValue\" .",
        "text/turtle",
        "testAuthor",
        "Merge new triples",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph("default", baseCommit, "http://example.org/graph1"))
        .thenReturn(currentGraph);
    when(rdfParsingService.parseRdf(anyString(), eq("text/turtle")))
        .thenReturn(newContent);
    when(graphDiffService.computePostDiff(currentGraph, newContent, "http://example.org/graph1"))
        .thenReturn(patch);
    when(graphDiffService.isPatchEmpty(patch)).thenReturn(false);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isInstanceOf(CommitCreatedEvent.class);
    CommitCreatedEvent commitEvent = (CommitCreatedEvent) event;
    assertThat(commitEvent.dataset()).isEqualTo("default");
    assertThat(commitEvent.author()).isEqualTo("testAuthor");
    assertThat(commitEvent.message()).isEqualTo("Merge new triples");
    assertThat(commitEvent.parents()).contains(baseCommit.value());

    verify(preconditionService).checkIfMatch("default", "main", null);
  }

  @Test
  void handle_shouldReturnNull_whenAllTriplesAlreadyPresent() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model currentGraph = ModelFactory.createDefaultModel();
    currentGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

    Model newContent = ModelFactory.createDefaultModel();
    newContent.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

    RDFPatch emptyPatch = RDFPatchOps.emptyPatch();

    PostGraphCommand command = new PostGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \"value\" .",
        "text/turtle",
        "testAuthor",
        "No-op merge",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph("default", baseCommit, "http://example.org/graph1"))
        .thenReturn(currentGraph);
    when(rdfParsingService.parseRdf(anyString(), eq("text/turtle")))
        .thenReturn(newContent);
    when(graphDiffService.computePostDiff(currentGraph, newContent, "http://example.org/graph1"))
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
    Model newContent = ModelFactory.createDefaultModel();
    newContent.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

    RDFPatch patch = RDFPatchOps.emptyPatch();

    PostGraphCommand command = new PostGraphCommand(
        "default",
        null,
        true,
        "main",
        baseCommit,
        "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \"value\" .",
        "text/turtle",
        "testAuthor",
        "Merge to default graph",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getDefaultGraph("default", baseCommit))
        .thenReturn(currentGraph);
    when(rdfParsingService.parseRdf(anyString(), eq("text/turtle")))
        .thenReturn(newContent);
    when(graphDiffService.computePostDiff(currentGraph, newContent, null))
        .thenReturn(patch);
    when(graphDiffService.isPatchEmpty(patch)).thenReturn(false);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isNotNull();
    verify(datasetService).getDefaultGraph("default", baseCommit);
    verify(graphDiffService).computePostDiff(currentGraph, newContent, null);
  }

  @Test
  void handle_shouldCheckPrecondition_whenIfMatchProvided() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model currentGraph = ModelFactory.createDefaultModel();
    Model newContent = ModelFactory.createDefaultModel();
    RDFPatch patch = RDFPatchOps.emptyPatch();

    PostGraphCommand command = new PostGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        "",
        "text/turtle",
        "testAuthor",
        "Merge graph",
        "\"" + baseCommit.value() + "\""
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph(anyString(), any(), anyString()))
        .thenReturn(currentGraph);
    when(rdfParsingService.parseRdf(anyString(), anyString()))
        .thenReturn(newContent);
    when(graphDiffService.computePostDiff(any(), any(), anyString()))
        .thenReturn(patch);
    when(graphDiffService.isPatchEmpty(patch)).thenReturn(false);

    // When
    handler.handle(command);

    // Then
    verify(preconditionService).checkIfMatch(
        "default", "main", "\"" + baseCommit.value() + "\"");
  }

  @Test
  void handle_shouldThrowPreconditionFailed_whenETagMismatch() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    PostGraphCommand command = new PostGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        "",
        "text/turtle",
        "testAuthor",
        "Merge graph",
        "\"wrongETag\""
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    doThrow(new PreconditionFailedException("wrongETag", baseCommit.value()))
        .when(preconditionService).checkIfMatch(anyString(), anyString(), anyString());

    // When/Then
    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(PreconditionFailedException.class);

    verify(datasetService, never()).getGraph(anyString(), any(), anyString());
  }

  @Test
  void handle_shouldThrowException_whenBranchNotFound() {
    // Given
    CommitId baseCommit = CommitId.generate();

    PostGraphCommand command = new PostGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "nonexistent",
        baseCommit,
        "",
        "text/turtle",
        "testAuthor",
        "Merge graph",
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

    Model newContent = ModelFactory.createDefaultModel();
    newContent.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

    RDFPatch patch = RDFPatchOps.emptyPatch();

    PostGraphCommand command = new PostGraphCommand(
        "default",
        "http://example.org/newGraph",
        false,
        "main",
        baseCommit,
        "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \"value\" .",
        "text/turtle",
        "testAuthor",
        "Create new graph with POST",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph("default", baseCommit, "http://example.org/newGraph"))
        .thenReturn(null); // Graph doesn't exist
    when(rdfParsingService.parseRdf(anyString(), eq("text/turtle")))
        .thenReturn(newContent);
    when(graphDiffService.computePostDiff(any(Model.class), eq(newContent),
            eq("http://example.org/newGraph")))
        .thenReturn(patch);
    when(graphDiffService.isPatchEmpty(patch)).thenReturn(false);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isNotNull();
    // Should compute diff with empty model when graph doesn't exist
    verify(graphDiffService).computePostDiff(any(Model.class), eq(newContent),
        eq("http://example.org/newGraph"));
  }
}
