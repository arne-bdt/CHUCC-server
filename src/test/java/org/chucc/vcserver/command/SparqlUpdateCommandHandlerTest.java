package org.chucc.vcserver.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.mem.DatasetGraphInMemory;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.exception.MalformedUpdateException;
import org.chucc.vcserver.exception.PreconditionFailedException;
import org.chucc.vcserver.exception.UpdateExecutionException;
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
 * Unit tests for SparqlUpdateCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class SparqlUpdateCommandHandlerTest {

  @Mock
  private EventPublisher eventPublisher;

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private DatasetService datasetService;

  @Mock
  private GraphDiffService graphDiffService;

  @Mock
  private ConflictDetectionService conflictDetectionService;

  @Mock
  private PreconditionService preconditionService;

  private SparqlUpdateCommandHandler handler;

  @BeforeEach
  void setUp() {
    // Mock EventPublisher.publish() to return completed future (lenient for all tests)
    lenient().when(eventPublisher.publish(any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    handler = new SparqlUpdateCommandHandler(
        eventPublisher,
        branchRepository,
        datasetService,
        graphDiffService,
        conflictDetectionService,
        preconditionService
    );
  }

  @Test
  void handle_shouldCreateCommit_whenUpdateModifiesDataset() {
    // Given
    CommitId headCommit = CommitId.generate();
    Branch branch = new Branch("main", headCommit);
    DatasetGraph dataset = new DatasetGraphInMemory();

    String updateString = "INSERT DATA { <http://example.org/s> "
        + "<http://example.org/p> \"value\" }";

    SparqlUpdateCommand command = new SparqlUpdateCommand(
        "default",
        "main",
        updateString,
        "testAuthor",
        "Add triple",
        Optional.empty()
    );

    RDFPatch nonEmptyPatch = RDFPatchOps.emptyPatch(); // Mock non-empty

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.materializeCommit("default", headCommit))
        .thenReturn(dataset);
    when(graphDiffService.isPatchEmpty(any())).thenReturn(false);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isInstanceOf(CommitCreatedEvent.class);
    CommitCreatedEvent commitEvent = (CommitCreatedEvent) event;
    assertThat(commitEvent.dataset()).isEqualTo("default");
    assertThat(commitEvent.branch()).isEqualTo("main");
    assertThat(commitEvent.author()).isEqualTo("testAuthor");
    assertThat(commitEvent.message()).isEqualTo("Add triple");
    assertThat(commitEvent.parents()).contains(headCommit.value());

    verify(eventPublisher).publish(any(CommitCreatedEvent.class));
  }

  @Test
  void handle_shouldReturnNull_whenUpdateResultsInNoOp() {
    // Given
    CommitId headCommit = CommitId.generate();
    Branch branch = new Branch("main", headCommit);
    DatasetGraph dataset = new DatasetGraphInMemory();

    // Update that deletes non-existent triple (no-op)
    String updateString = "DELETE DATA { <http://example.org/s> "
        + "<http://example.org/p> \"value\" }";

    SparqlUpdateCommand command = new SparqlUpdateCommand(
        "default",
        "main",
        updateString,
        "testAuthor",
        "No-op delete",
        Optional.empty()
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.materializeCommit("default", headCommit))
        .thenReturn(dataset);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isNull(); // No-op should return null
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void handle_shouldCheckIfMatchPrecondition_whenExpectedHeadProvided() {
    // Given
    CommitId headCommit = CommitId.generate();
    Branch branch = new Branch("main", headCommit);
    DatasetGraph dataset = new DatasetGraphInMemory();

    String updateString = "INSERT DATA { <http://example.org/s> "
        + "<http://example.org/p> \"value\" }";

    SparqlUpdateCommand command = new SparqlUpdateCommand(
        "default",
        "main",
        updateString,
        "testAuthor",
        "Add triple",
        Optional.of(headCommit) // If-Match precondition
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.materializeCommit("default", headCommit))
        .thenReturn(dataset);
    when(graphDiffService.isPatchEmpty(any())).thenReturn(false);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isNotNull();
    // Precondition passed (expectedHead == currentHead)
  }

  @Test
  void handle_shouldThrowPreconditionFailed_whenIfMatchMismatch() {
    // Given
    CommitId currentHead = CommitId.generate();
    CommitId expectedHead = CommitId.generate(); // Different from current
    Branch branch = new Branch("main", currentHead);

    String updateString = "INSERT DATA { <http://example.org/s> "
        + "<http://example.org/p> \"value\" }";

    SparqlUpdateCommand command = new SparqlUpdateCommand(
        "default",
        "main",
        updateString,
        "testAuthor",
        "Add triple",
        Optional.of(expectedHead) // Wrong ETag
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));

    // When/Then
    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(PreconditionFailedException.class)
        .hasMessageContaining("If-Match precondition failed")
        .hasMessageContaining(expectedHead.value())
        .hasMessageContaining(currentHead.value());

    verify(datasetService, never()).materializeCommit(anyString(), any());
  }

  @Test
  void handle_shouldThrowMalformedUpdateException_whenQueryHasSyntaxError() {
    // Given
    CommitId headCommit = CommitId.generate();
    Branch branch = new Branch("main", headCommit);
    DatasetGraph dataset = new DatasetGraphInMemory();

    // Malformed UPDATE (missing closing brace)
    String updateString = "INSERT DATA { <http://example.org/s> "
        + "<http://example.org/p> \"value\"";

    SparqlUpdateCommand command = new SparqlUpdateCommand(
        "default",
        "main",
        updateString,
        "testAuthor",
        "Bad syntax",
        Optional.empty()
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.materializeCommit("default", headCommit))
        .thenReturn(dataset);

    // When/Then
    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(MalformedUpdateException.class)
        .hasMessageContaining("malformed");

    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void handle_shouldThrowException_whenBranchNotFound() {
    // Given
    String updateString = "INSERT DATA { <http://example.org/s> "
        + "<http://example.org/p> \"value\" }";

    SparqlUpdateCommand command = new SparqlUpdateCommand(
        "default",
        "nonexistent",
        updateString,
        "testAuthor",
        "Add triple",
        Optional.empty()
    );

    when(branchRepository.findByDatasetAndName("default", "nonexistent"))
        .thenReturn(Optional.empty());

    // When/Then
    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Branch not found");
  }

  @Test
  void handle_shouldExecuteComplexUpdate() {
    // Given
    CommitId headCommit = CommitId.generate();
    Branch branch = new Branch("main", headCommit);
    DatasetGraph dataset = new DatasetGraphInMemory();

    // Add initial data to dataset so DELETE/INSERT WHERE has something to match
    org.apache.jena.graph.Node graphNode =
        org.apache.jena.sparql.core.Quad.defaultGraphNodeGenerated;
    org.apache.jena.graph.Node subject =
        org.apache.jena.graph.NodeFactory.createURI("http://example.org/s");
    org.apache.jena.graph.Node predicate =
        org.apache.jena.graph.NodeFactory.createURI("http://example.org/p");
    org.apache.jena.graph.Node object =
        org.apache.jena.graph.NodeFactory.createLiteralString("value");
    dataset.add(graphNode, subject, predicate, object);

    // Complex update with DELETE/INSERT WHERE
    String updateString = "DELETE { ?s <http://example.org/p> ?o } "
        + "INSERT { ?s <http://example.org/p2> ?o } "
        + "WHERE { ?s <http://example.org/p> ?o }";

    SparqlUpdateCommand command = new SparqlUpdateCommand(
        "default",
        "main",
        updateString,
        "testAuthor",
        "Rename predicate",
        Optional.empty()
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.materializeCommit("default", headCommit))
        .thenReturn(dataset);
    when(graphDiffService.isPatchEmpty(any())).thenReturn(false);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isNotNull();
    assertThat(event).isInstanceOf(CommitCreatedEvent.class);
  }

  @Test
  void handle_shouldHandleMultipleGraphUpdates() {
    // Given
    CommitId headCommit = CommitId.generate();
    Branch branch = new Branch("main", headCommit);
    DatasetGraph dataset = new DatasetGraphInMemory();

    // Update affecting multiple graphs
    String updateString = "INSERT DATA { "
        + "GRAPH <http://example.org/g1> { <http://example.org/s> "
        + "<http://example.org/p> \"v1\" } . "
        + "GRAPH <http://example.org/g2> { <http://example.org/s> "
        + "<http://example.org/p> \"v2\" } "
        + "}";

    SparqlUpdateCommand command = new SparqlUpdateCommand(
        "default",
        "main",
        updateString,
        "testAuthor",
        "Add to multiple graphs",
        Optional.empty()
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.materializeCommit("default", headCommit))
        .thenReturn(dataset);
    when(graphDiffService.isPatchEmpty(any())).thenReturn(false);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isNotNull();
    assertThat(event).isInstanceOf(CommitCreatedEvent.class);
  }
}
