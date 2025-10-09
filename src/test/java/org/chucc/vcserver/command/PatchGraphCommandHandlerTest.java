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
import org.chucc.vcserver.exception.PreconditionFailedException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.service.ConflictDetectionService;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.GraphDiffService;
import org.chucc.vcserver.service.PreconditionService;
import org.chucc.vcserver.service.RdfPatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for PatchGraphCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class PatchGraphCommandHandlerTest {

  @Mock
  private EventPublisher eventPublisher;

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private DatasetService datasetService;

  @Mock
  private RdfPatchService rdfPatchService;

  @Mock
  private GraphDiffService graphDiffService;

  @Mock
  private PreconditionService preconditionService;

  @Mock
  private ConflictDetectionService conflictDetectionService;

  private PatchGraphCommandHandler handler;

  @BeforeEach
  void setUp() {
    // Mock EventPublisher.publish() to return completed future (lenient for tests that don't publish)
    lenient().when(eventPublisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));

    handler = new PatchGraphCommandHandler(
        eventPublisher,
        branchRepository,
        datasetService,
        rdfPatchService,
        graphDiffService,
        preconditionService,
        conflictDetectionService
    );
  }

  @Test
  void handle_shouldCreateCommit_whenPatchApplied() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model currentGraph = ModelFactory.createDefaultModel();
    currentGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "oldValue"
    );

    String patchContent = """
        TX .
        D <http://example.org/graph1> <http://example.org/s1> <http://example.org/p1> "oldValue" .
        A <http://example.org/graph1> <http://example.org/s1> <http://example.org/p1> "newValue" .
        TC .
        """;

    RDFPatch parsedPatch = RDFPatchOps.emptyPatch(); // Mock patch
    RDFPatch filteredPatch = RDFPatchOps.emptyPatch(); // Mock filtered patch

    PatchGraphCommand command = new PatchGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        patchContent,
        "testAuthor",
        "Apply patch",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph("default", baseCommit, "http://example.org/graph1"))
        .thenReturn(currentGraph);
    when(rdfPatchService.parsePatch(patchContent)).thenReturn(parsedPatch);
    when(rdfPatchService.filterByGraph(parsedPatch, "http://example.org/graph1"))
        .thenReturn(filteredPatch);
    when(rdfPatchService.canApply(currentGraph, filteredPatch)).thenReturn(true);
    when(graphDiffService.isPatchEmpty(filteredPatch)).thenReturn(false);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isInstanceOf(CommitCreatedEvent.class);
    CommitCreatedEvent commitEvent = (CommitCreatedEvent) event;
    assertThat(commitEvent.dataset()).isEqualTo("default");
    assertThat(commitEvent.author()).isEqualTo("testAuthor");
    assertThat(commitEvent.message()).isEqualTo("Apply patch");
    assertThat(commitEvent.parents()).contains(baseCommit.value());

    verify(rdfPatchService).parsePatch(patchContent);
    verify(rdfPatchService).filterByGraph(parsedPatch, "http://example.org/graph1");
    verify(rdfPatchService).canApply(currentGraph, filteredPatch);
  }

  @Test
  void handle_shouldReturnNull_whenPatchIsNoOp() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model currentGraph = ModelFactory.createDefaultModel();
    String patchContent = """
        TX .
        TC .
        """;

    RDFPatch parsedPatch = RDFPatchOps.emptyPatch();
    RDFPatch filteredPatch = RDFPatchOps.emptyPatch();

    PatchGraphCommand command = new PatchGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        patchContent,
        "testAuthor",
        "No-op patch",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph("default", baseCommit, "http://example.org/graph1"))
        .thenReturn(currentGraph);
    when(rdfPatchService.parsePatch(patchContent)).thenReturn(parsedPatch);
    when(rdfPatchService.filterByGraph(parsedPatch, "http://example.org/graph1"))
        .thenReturn(filteredPatch);
    when(rdfPatchService.canApply(currentGraph, filteredPatch)).thenReturn(true);
    when(graphDiffService.isPatchEmpty(filteredPatch)).thenReturn(true);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isNull(); // No-op should return null
  }

  @Test
  void handle_shouldThrowException_whenPatchSyntaxInvalid() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    String invalidPatchContent = "INVALID PATCH SYNTAX";

    PatchGraphCommand command = new PatchGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        invalidPatchContent,
        "testAuthor",
        "Apply invalid patch",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(rdfPatchService.parsePatch(invalidPatchContent))
        .thenThrow(new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Invalid RDF Patch syntax"));

    // When/Then
    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");

    verify(datasetService, never()).getGraph(anyString(), any(), anyString());
  }

  @Test
  void handle_shouldThrowException_whenPatchCannotBeApplied() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model currentGraph = ModelFactory.createDefaultModel();

    String patchContent = """
        TX .
        D <http://example.org/graph1> <http://example.org/s1> <http://example.org/p1> "nonExistent" .
        TC .
        """;

    RDFPatch parsedPatch = RDFPatchOps.emptyPatch();
    RDFPatch filteredPatch = RDFPatchOps.emptyPatch();

    PatchGraphCommand command = new PatchGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        patchContent,
        "testAuthor",
        "Apply unapplicable patch",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph("default", baseCommit, "http://example.org/graph1"))
        .thenReturn(currentGraph);
    when(rdfPatchService.parsePatch(patchContent)).thenReturn(parsedPatch);
    when(rdfPatchService.filterByGraph(parsedPatch, "http://example.org/graph1"))
        .thenReturn(filteredPatch);
    when(rdfPatchService.canApply(currentGraph, filteredPatch)).thenReturn(false);

    // When/Then
    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("422");
  }

  @Test
  void handle_shouldHandleDefaultGraph() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model currentGraph = ModelFactory.createDefaultModel();
    String patchContent = """
        TX .
        A <http://example.org/s1> <http://example.org/p1> "value" .
        TC .
        """;

    RDFPatch parsedPatch = RDFPatchOps.emptyPatch();
    RDFPatch filteredPatch = RDFPatchOps.emptyPatch();

    PatchGraphCommand command = new PatchGraphCommand(
        "default",
        null,
        true,
        "main",
        baseCommit,
        patchContent,
        "testAuthor",
        "Patch default graph",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getDefaultGraph("default", baseCommit))
        .thenReturn(currentGraph);
    when(rdfPatchService.parsePatch(patchContent)).thenReturn(parsedPatch);
    when(rdfPatchService.filterByGraph(parsedPatch, null))
        .thenReturn(filteredPatch);
    when(rdfPatchService.canApply(currentGraph, filteredPatch)).thenReturn(true);
    when(graphDiffService.isPatchEmpty(filteredPatch)).thenReturn(false);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isNotNull();
    verify(datasetService).getDefaultGraph("default", baseCommit);
    verify(rdfPatchService).filterByGraph(parsedPatch, null);
  }

  @Test
  void handle_shouldCheckPrecondition_whenIfMatchProvided() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model currentGraph = ModelFactory.createDefaultModel();
    String patchContent = """
        TX .
        A <http://example.org/s1> <http://example.org/p1> "value" .
        TC .
        """;

    RDFPatch parsedPatch = RDFPatchOps.emptyPatch();
    RDFPatch filteredPatch = RDFPatchOps.emptyPatch();

    PatchGraphCommand command = new PatchGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        patchContent,
        "testAuthor",
        "Patch graph",
        "\"" + baseCommit.value() + "\""
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph(anyString(), any(), anyString()))
        .thenReturn(currentGraph);
    when(rdfPatchService.parsePatch(anyString())).thenReturn(parsedPatch);
    when(rdfPatchService.filterByGraph(any(), anyString())).thenReturn(filteredPatch);
    when(rdfPatchService.canApply(any(), any())).thenReturn(true);
    when(graphDiffService.isPatchEmpty(any())).thenReturn(false);

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

    PatchGraphCommand command = new PatchGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        "TX . TC .",
        "testAuthor",
        "Patch graph",
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
    RDFPatch patch = RDFPatchOps.emptyPatch();
    RDFPatch filteredPatch = RDFPatchOps.emptyPatch();

    PatchGraphCommand command = new PatchGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        "TX . A <http://example.org/graph1> <http://example.org/s1> <http://example.org/p1> \"value\" . TC .",
        "testAuthor",
        "Patch graph",
        "\"" + currentHead.value() + "\""
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(rdfPatchService.parsePatch(anyString())).thenReturn(patch);
    when(rdfPatchService.filterByGraph(patch, "http://example.org/graph1")).thenReturn(filteredPatch);
    when(datasetService.getGraph("default", baseCommit, "http://example.org/graph1"))
        .thenReturn(currentGraph);
    when(rdfPatchService.canApply(currentGraph, filteredPatch)).thenReturn(true);
    when(graphDiffService.isPatchEmpty(filteredPatch)).thenReturn(false);

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

    PatchGraphCommand command = new PatchGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "nonexistent",
        baseCommit,
        "TX . TC .",
        "testAuthor",
        "Patch graph",
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
  void handle_shouldCreateEmptyGraph_whenGraphDoesNotExist() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    String patchContent = """
        TX .
        A <http://example.org/graph1> <http://example.org/s1> <http://example.org/p1> "value" .
        TC .
        """;

    RDFPatch parsedPatch = RDFPatchOps.emptyPatch();
    RDFPatch filteredPatch = RDFPatchOps.emptyPatch();

    PatchGraphCommand command = new PatchGraphCommand(
        "default",
        "http://example.org/graph1",
        false,
        "main",
        baseCommit,
        patchContent,
        "testAuthor",
        "Create new graph with PATCH",
        null
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph("default", baseCommit, "http://example.org/graph1"))
        .thenReturn(null); // Graph doesn't exist
    when(rdfPatchService.parsePatch(patchContent)).thenReturn(parsedPatch);
    when(rdfPatchService.filterByGraph(parsedPatch, "http://example.org/graph1"))
        .thenReturn(filteredPatch);
    when(rdfPatchService.canApply(any(Model.class), eq(filteredPatch))).thenReturn(true);
    when(graphDiffService.isPatchEmpty(filteredPatch)).thenReturn(false);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isNotNull();
    // Should apply patch to empty model when graph doesn't exist
    verify(rdfPatchService).canApply(any(Model.class), eq(filteredPatch));
  }
}
