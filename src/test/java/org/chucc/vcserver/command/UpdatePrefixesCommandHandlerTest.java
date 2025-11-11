package org.chucc.vcserver.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.system.PrefixMapFactory;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.mem.DatasetGraphInMemory;
import org.apache.jena.sparql.graph.GraphFactory;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for UpdatePrefixesCommandHandler.
 * Tests RDFPatch generation logic for PA/PD directives.
 */
class UpdatePrefixesCommandHandlerTest {

  private UpdatePrefixesCommandHandler handler;
  private MaterializedBranchRepository materializedBranchRepository;
  private BranchRepository branchRepository;
  private CreateCommitCommandHandler createCommitCommandHandler;

  @BeforeEach
  void setUp() {
    materializedBranchRepository = mock(MaterializedBranchRepository.class);
    branchRepository = mock(BranchRepository.class);
    createCommitCommandHandler = mock(CreateCommitCommandHandler.class);

    handler = new UpdatePrefixesCommandHandler(
        materializedBranchRepository,
        branchRepository,
        createCommitCommandHandler
    );
  }

  @Test
  void buildPrefixPatch_shouldGeneratePaDirectives_forPatchOperation() {
    // Arrange
    Map<String, String> oldPrefixes = Map.of();
    Map<String, String> newPrefixes = Map.of(
        "foaf", "http://xmlns.com/foaf/0.1/",
        "geo", "http://www.opengis.net/ont/geosparql#"
    );

    // Act
    RDFPatch patch = handler.buildPrefixPatch(
        oldPrefixes,
        newPrefixes,
        UpdatePrefixesCommand.Operation.PATCH
    );

    // Assert
    String patchStr = RDFPatchOps.str(patch);
    assertThat(patchStr).contains("TX .");
    assertThat(patchStr).contains("PA \"foaf\" \"http://xmlns.com/foaf/0.1/\" .");
    assertThat(patchStr).contains("PA \"geo\" \"http://www.opengis.net/ont/geosparql#\" .");
    assertThat(patchStr).contains("TC .");
    assertThat(patchStr).doesNotContain("PD");  // No deletions
  }

  @Test
  void buildPrefixPatch_shouldGeneratePdThenPa_forPutOperation() {
    // Arrange
    Map<String, String> oldPrefixes = Map.of(
        "old1", "http://example.org/old1/",
        "old2", "http://example.org/old2/"
    );
    Map<String, String> newPrefixes = Map.of(
        "new", "http://example.org/new/"
    );

    // Act
    RDFPatch patch = handler.buildPrefixPatch(
        oldPrefixes,
        newPrefixes,
        UpdatePrefixesCommand.Operation.PUT
    );

    // Assert
    String patchStr = RDFPatchOps.str(patch);
    assertThat(patchStr).contains("TX .");
    assertThat(patchStr).contains("PD \"old1\" .");
    assertThat(patchStr).contains("PD \"old2\" .");
    assertThat(patchStr).contains("PA \"new\" \"http://example.org/new/\" .");
    assertThat(patchStr).contains("TC .");
  }

  @Test
  void buildPrefixPatch_shouldGeneratePdDirectives_forDeleteOperation() {
    // Arrange
    Map<String, String> oldPrefixes = Map.of();
    Map<String, String> prefixesToDelete = Map.of(
        "temp", "",
        "test", ""
    );

    // Act
    RDFPatch patch = handler.buildPrefixPatch(
        oldPrefixes,
        prefixesToDelete,
        UpdatePrefixesCommand.Operation.DELETE
    );

    // Assert
    String patchStr = RDFPatchOps.str(patch);
    assertThat(patchStr).contains("TX .");
    assertThat(patchStr).contains("PD \"temp\" .");
    assertThat(patchStr).contains("PD \"test\" .");
    assertThat(patchStr).contains("TC .");
    assertThat(patchStr).doesNotContain("PA");  // No additions
  }

  @Test
  void buildPrefixPatch_shouldHandleEmptyPrefixMap_forPutOperation() {
    // Arrange - PUT with empty map should remove all old prefixes
    Map<String, String> oldPrefixes = Map.of(
        "rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
        "rdfs", "http://www.w3.org/2000/01/rdf-schema#"
    );
    Map<String, String> newPrefixes = Map.of();

    // Act
    RDFPatch patch = handler.buildPrefixPatch(
        oldPrefixes,
        newPrefixes,
        UpdatePrefixesCommand.Operation.PUT
    );

    // Assert
    String patchStr = RDFPatchOps.str(patch);
    assertThat(patchStr).contains("TX .");
    assertThat(patchStr).contains("PD \"rdf\" .");
    assertThat(patchStr).contains("PD \"rdfs\" .");
    assertThat(patchStr).contains("TC .");
    assertThat(patchStr).doesNotContain("PA");  // No additions
  }

  @Test
  void handle_shouldThrowBranchNotFoundException_whenBranchDoesNotExist() {
    // Arrange
    UpdatePrefixesCommand cmd = new UpdatePrefixesCommand(
        "test-dataset",
        "nonexistent",
        "TestUser",
        Map.of("ex", "http://example.org/"),
        UpdatePrefixesCommand.Operation.PUT,
        Optional.empty()
    );

    when(branchRepository.findByDatasetAndName("test-dataset", "nonexistent"))
        .thenReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> handler.handle(cmd))
        .isInstanceOf(BranchNotFoundException.class)
        .hasMessageContaining("nonexistent");
  }

  @Test
  void handle_shouldDelegateToCreateCommitCommandHandler() {
    // Arrange
    String dataset = "test-dataset";
    String branch = "main";
    CommitId headCommitId = CommitId.generate();

    Branch mockBranch = new Branch(branch, headCommitId);
    when(branchRepository.findByDatasetAndName(dataset, branch))
        .thenReturn(Optional.of(mockBranch));

    DatasetGraph mockDsg = new DatasetGraphInMemory();
    mockDsg.getDefaultGraph().getPrefixMapping().setNsPrefix("old", "http://old.org/");
    when(materializedBranchRepository.getBranchGraph(dataset, branch))
        .thenReturn(mockDsg);

    CommitCreatedEvent mockEvent = mock(CommitCreatedEvent.class);
    when(createCommitCommandHandler.handle(any(CreateCommitCommand.class)))
        .thenReturn(mockEvent);

    UpdatePrefixesCommand cmd = new UpdatePrefixesCommand(
        dataset,
        branch,
        "TestUser",
        Map.of("new", "http://new.org/"),
        UpdatePrefixesCommand.Operation.PUT,
        Optional.of("Test message")
    );

    // Act
    CommitCreatedEvent result = handler.handle(cmd);

    // Assert
    assertThat(result).isEqualTo(mockEvent);
    verify(createCommitCommandHandler).handle(any(CreateCommitCommand.class));
  }

  @Test
  void handle_shouldUseDefaultMessage_whenMessageNotProvided() {
    // Arrange
    String dataset = "test-dataset";
    String branch = "main";
    CommitId headCommitId = CommitId.generate();

    Branch mockBranch = new Branch(branch, headCommitId);
    when(branchRepository.findByDatasetAndName(dataset, branch))
        .thenReturn(Optional.of(mockBranch));

    DatasetGraph mockDsg = new DatasetGraphInMemory();
    when(materializedBranchRepository.getBranchGraph(dataset, branch))
        .thenReturn(mockDsg);

    CommitCreatedEvent mockEvent = mock(CommitCreatedEvent.class);
    when(createCommitCommandHandler.handle(any(CreateCommitCommand.class)))
        .thenReturn(mockEvent);

    UpdatePrefixesCommand cmd = new UpdatePrefixesCommand(
        dataset,
        branch,
        "TestUser",
        Map.of("foaf", "http://xmlns.com/foaf/0.1/", "geo", "http://www.opengis.net/ont/geosparql#"),
        UpdatePrefixesCommand.Operation.PATCH,
        Optional.empty()  // No message
    );

    // Act
    handler.handle(cmd);

    // Assert
    verify(createCommitCommandHandler).handle(any(CreateCommitCommand.class));
    // Default message for PATCH: "Add prefixes: foaf, geo" (or similar)
  }
}
