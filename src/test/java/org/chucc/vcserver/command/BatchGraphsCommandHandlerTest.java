package org.chucc.vcserver.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.chucc.vcserver.command.BatchGraphsCommand.GraphOperation;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.BatchGraphsCompletedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.service.ConflictDetectionService;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.GraphDiffService;
import org.chucc.vcserver.service.RdfParsingService;
import org.chucc.vcserver.service.RdfPatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for BatchGraphsCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class BatchGraphsCommandHandlerTest {

  @Mock
  private EventPublisher eventPublisher;

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private DatasetService datasetService;

  @Mock
  private RdfParsingService rdfParsingService;

  @Mock
  private RdfPatchService rdfPatchService;

  @Mock
  private GraphDiffService graphDiffService;

  @Mock
  private ConflictDetectionService conflictDetectionService;

  private BatchGraphsCommandHandler handler;

  @BeforeEach
  void setUp() {
    // Mock EventPublisher.publish() to return completed future (lenient for tests that don't publish)
    lenient().when(eventPublisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));

    handler = new BatchGraphsCommandHandler(
        eventPublisher,
        branchRepository,
        datasetService,
        rdfParsingService,
        rdfPatchService,
        graphDiffService,
        conflictDetectionService
    );
  }

  @Test
  void handle_shouldCreateSingleCommit_whenModeIsSingleAndChangesExist() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model graph1 = ModelFactory.createDefaultModel();
    graph1.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value1"
    );

    Model graph2 = ModelFactory.createDefaultModel();
    graph2.add(
        ResourceFactory.createResource("http://example.org/s2"),
        ResourceFactory.createProperty("http://example.org/p2"),
        "value2"
    );

    RDFPatch patch1 = RDFPatchOps.emptyPatch();
    RDFPatch patch2 = RDFPatchOps.emptyPatch();

    List<GraphOperation> operations = List.of(
        new GraphOperation("PUT", "http://example.org/g1", false,
            "data1", "text/turtle", null),
        new GraphOperation("PUT", "http://example.org/g2", false,
            "data2", "text/turtle", null)
    );

    BatchGraphsCommand command = new BatchGraphsCommand(
        "default",
        "main",
        baseCommit,
        operations,
        "testAuthor",
        "Batch operation",
        "single"
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph(eq("default"), eq(baseCommit), anyString()))
        .thenReturn(ModelFactory.createDefaultModel());
    when(rdfParsingService.parseRdf(eq("data1"), eq("text/turtle"))).thenReturn(graph1);
    when(rdfParsingService.parseRdf(eq("data2"), eq("text/turtle"))).thenReturn(graph2);
    when(graphDiffService.computePutDiff(any(), eq(graph1), eq("http://example.org/g1")))
        .thenReturn(patch1);
    when(graphDiffService.computePutDiff(any(), eq(graph2), eq("http://example.org/g2")))
        .thenReturn(patch2);
    when(graphDiffService.isPatchEmpty(any())).thenReturn(false);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isInstanceOf(BatchGraphsCompletedEvent.class);
    BatchGraphsCompletedEvent batchEvent = (BatchGraphsCompletedEvent) event;
    assertThat(batchEvent.commits()).hasSize(1);
  }

  @Test
  void handle_shouldReturnNoOp_whenModeIsSingleAndAllOperationsAreNoOps() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model emptyGraph = ModelFactory.createDefaultModel();
    RDFPatch emptyPatch = RDFPatchOps.emptyPatch();

    List<GraphOperation> operations = List.of(
        new GraphOperation("PUT", "http://example.org/g1", false,
            "@prefix ex: <http://example.org/> .", "text/turtle", null)
    );

    BatchGraphsCommand command = new BatchGraphsCommand(
        "default",
        "main",
        baseCommit,
        operations,
        "testAuthor",
        "Batch operation",
        "single"
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph(eq("default"), eq(baseCommit), anyString()))
        .thenReturn(emptyGraph);
    when(rdfParsingService.parseRdf(anyString(), eq("text/turtle"))).thenReturn(emptyGraph);
    when(graphDiffService.computePutDiff(any(), any(), anyString()))
        .thenReturn(emptyPatch);
    when(graphDiffService.isPatchEmpty(any())).thenReturn(true);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isNull();
  }

  @Test
  void handle_shouldCreateMultipleCommits_whenModeIsMultiple() {
    // Given
    CommitId baseCommit = CommitId.generate();
    Branch branch = new Branch("main", baseCommit);

    Model graph1 = ModelFactory.createDefaultModel();
    graph1.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value1"
    );

    Model graph2 = ModelFactory.createDefaultModel();
    graph2.add(
        ResourceFactory.createResource("http://example.org/s2"),
        ResourceFactory.createProperty("http://example.org/p2"),
        "value2"
    );

    RDFPatch patch1 = RDFPatchOps.emptyPatch();
    RDFPatch patch2 = RDFPatchOps.emptyPatch();

    List<GraphOperation> operations = List.of(
        new GraphOperation("PUT", "http://example.org/g1", false,
            "data1", "text/turtle", null),
        new GraphOperation("PUT", "http://example.org/g2", false,
            "data2", "text/turtle", null)
    );

    BatchGraphsCommand command = new BatchGraphsCommand(
        "default",
        "main",
        baseCommit,
        operations,
        "testAuthor",
        "Batch operation",
        "multiple"
    );

    when(branchRepository.findByDatasetAndName("default", "main"))
        .thenReturn(Optional.of(branch));
    when(datasetService.getGraph(eq("default"), any(), anyString()))
        .thenReturn(ModelFactory.createDefaultModel());
    when(rdfParsingService.parseRdf(eq("data1"), eq("text/turtle"))).thenReturn(graph1);
    when(rdfParsingService.parseRdf(eq("data2"), eq("text/turtle"))).thenReturn(graph2);
    when(graphDiffService.computePutDiff(any(), eq(graph1), eq("http://example.org/g1")))
        .thenReturn(patch1);
    when(graphDiffService.computePutDiff(any(), eq(graph2), eq("http://example.org/g2")))
        .thenReturn(patch2);
    when(graphDiffService.isPatchEmpty(any())).thenReturn(false);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertThat(event).isInstanceOf(BatchGraphsCompletedEvent.class);
    BatchGraphsCompletedEvent batchEvent = (BatchGraphsCompletedEvent) event;
    assertThat(batchEvent.commits()).hasSize(2);
  }

  @Test
  void handle_shouldThrowException_whenBranchNotFound() {
    // Given
    CommitId baseCommit = CommitId.generate();
    List<GraphOperation> operations = List.of(
        new GraphOperation("PUT", "http://example.org/g1", false,
            "data1", "text/turtle", null)
    );

    BatchGraphsCommand command = new BatchGraphsCommand(
        "default",
        "nonexistent",
        baseCommit,
        operations,
        "testAuthor",
        "Batch operation",
        "single"
    );

    when(branchRepository.findByDatasetAndName("default", "nonexistent"))
        .thenReturn(Optional.empty());

    // When/Then
    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Branch not found");
  }
}
