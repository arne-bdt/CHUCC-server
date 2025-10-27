package org.chucc.vcserver.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.core.DatasetGraph;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for InMemoryMaterializedBranchRepository.
 *
 * <p>Tests the repository's core functionality:
 * <ul>
 * <li>Graph lifecycle (create, retrieve, delete)</li>
 * <li>Patch application with transactions</li>
 * <li>Branch cloning</li>
 * <li>Error handling</li>
 * </ul>
 */
class InMemoryMaterializedBranchRepositoryTest {

  private InMemoryMaterializedBranchRepository repository;

  /**
   * Set up test fixture before each test.
   */
  @BeforeEach
  void setUp() {
    repository = new InMemoryMaterializedBranchRepository(new SimpleMeterRegistry());
    repository.initializeMetrics();
  }

  @Test
  void getBranchGraph_shouldCreateNewGraphIfNotExists() {
    // Act
    DatasetGraph graph = repository.getBranchGraph("dataset1", "main");

    // Assert
    assertThat(graph).isNotNull();
    assertThat(repository.exists("dataset1", "main")).isTrue();
    assertThat(repository.getGraphCount()).isEqualTo(1);
  }

  @Test
  void getBranchGraph_shouldReturnSameGraphOnMultipleCalls() {
    // Act
    DatasetGraph graph1 = repository.getBranchGraph("dataset1", "main");
    DatasetGraph graph2 = repository.getBranchGraph("dataset1", "main");

    // Assert
    assertThat(graph1).isSameAs(graph2);
    assertThat(repository.getGraphCount()).isEqualTo(1);
  }

  @Test
  void applyPatchToBranch_shouldApplyPatchSuccessfully() {
    // Arrange
    String patchStr = """
        TX .
        A <http://example.org/s> <http://example.org/p> "value" .
        TC .
        """;
    RDFPatch patch = parsePatch(patchStr);

    // Act
    repository.applyPatchToBranch("dataset1", "main", patch);

    // Assert
    DatasetGraph graph = repository.getBranchGraph("dataset1", "main");
    graph.begin(ReadWrite.READ);
    try {
      assertThat(graph.isEmpty()).isFalse();

      Node subject = NodeFactory.createURI("http://example.org/s");
      Node predicate = NodeFactory.createURI("http://example.org/p");
      Node object = NodeFactory.createLiteralString("value");

      assertThat(graph.getDefaultGraph().contains(subject, predicate, object)).isTrue();
    } finally {
      graph.end();
    }
  }

  @Test
  void applyPatchToBranch_shouldApplyMultiplePatches() {
    // Arrange
    String patch1Str = """
        TX .
        A <http://example.org/s1> <http://example.org/p> "value1" .
        TC .
        """;
    String patch2Str = """
        TX .
        A <http://example.org/s2> <http://example.org/p> "value2" .
        TC .
        """;

    // Act
    repository.applyPatchToBranch("dataset1", "main", parsePatch(patch1Str));
    repository.applyPatchToBranch("dataset1", "main", parsePatch(patch2Str));

    // Assert
    DatasetGraph graph = repository.getBranchGraph("dataset1", "main");
    graph.begin(ReadWrite.READ);
    try {
      Node s1 = NodeFactory.createURI("http://example.org/s1");
      Node s2 = NodeFactory.createURI("http://example.org/s2");
      Node predicate = NodeFactory.createURI("http://example.org/p");
      Node obj1 = NodeFactory.createLiteralString("value1");
      Node obj2 = NodeFactory.createLiteralString("value2");

      assertThat(graph.getDefaultGraph().contains(s1, predicate, obj1)).isTrue();
      assertThat(graph.getDefaultGraph().contains(s2, predicate, obj2)).isTrue();
    } finally {
      graph.end();
    }
  }

  @Test
  void applyPatchToBranch_shouldThrowExceptionOnInvalidPatch() {
    // Arrange: Patch with invalid syntax (malformed URI)
    String invalidPatchStr = """
        TX .
        A <invalid uri with spaces> <http://example.org/p> "value" .
        TC .
        """;

    // Act & Assert
    assertThatThrownBy(() -> {
      RDFPatch patch = parsePatch(invalidPatchStr);
      repository.applyPatchToBranch("dataset1", "main", patch);
    }).isInstanceOf(Exception.class);  // Could be parse exception or application exception
  }

  @Test
  void createBranch_shouldCreateEmptyBranchWhenNoParent() {
    // Act
    repository.createBranch("dataset1", "feature", Optional.empty());

    // Assert
    assertThat(repository.exists("dataset1", "feature")).isTrue();
    DatasetGraph graph = repository.getBranchGraph("dataset1", "feature");

    graph.begin(ReadWrite.READ);
    try {
      assertThat(graph.isEmpty()).isTrue();
    } finally {
      graph.end();
    }
  }

  @Test
  void createBranch_shouldCloneParentBranch() {
    // Arrange: Create main branch with data
    String patchStr = """
        TX .
        A <http://example.org/s> <http://example.org/p> "value" .
        TC .
        """;
    repository.applyPatchToBranch("dataset1", "main", parsePatch(patchStr));

    // Act: Create feature branch from main
    repository.createBranch("dataset1", "feature", Optional.of("main"));

    // Assert: Feature branch has same data
    DatasetGraph featureGraph = repository.getBranchGraph("dataset1", "feature");
    featureGraph.begin(ReadWrite.READ);
    try {
      assertThat(featureGraph.isEmpty()).isFalse();
      Node subject = NodeFactory.createURI("http://example.org/s");
      Node predicate = NodeFactory.createURI("http://example.org/p");
      Node object = NodeFactory.createLiteralString("value");
      assertThat(featureGraph.getDefaultGraph().contains(subject, predicate, object)).isTrue();
    } finally {
      featureGraph.end();
    }

    // Assert: Branches are independent (different instances)
    DatasetGraph mainGraph = repository.getBranchGraph("dataset1", "main");
    assertThat(mainGraph).isNotSameAs(featureGraph);
  }

  @Test
  void createBranch_shouldMaintainIndependenceAfterCloning() {
    // Arrange: Create and populate main branch
    String patch1Str = """
        TX .
        A <http://example.org/s1> <http://example.org/p> "main-value" .
        TC .
        """;
    repository.applyPatchToBranch("dataset1", "main", parsePatch(patch1Str));

    // Clone to feature branch
    repository.createBranch("dataset1", "feature", Optional.of("main"));

    // Act: Add different data to each branch
    String patch2Str = """
        TX .
        A <http://example.org/s2> <http://example.org/p> "main-only" .
        TC .
        """;
    String patch3Str = """
        TX .
        A <http://example.org/s3> <http://example.org/p> "feature-only" .
        TC .
        """;
    repository.applyPatchToBranch("dataset1", "main", parsePatch(patch2Str));
    repository.applyPatchToBranch("dataset1", "feature", parsePatch(patch3Str));

    // Assert: Main branch has main-only data
    DatasetGraph mainGraph = repository.getBranchGraph("dataset1", "main");
    mainGraph.begin(ReadWrite.READ);
    try {
      Node s2 = NodeFactory.createURI("http://example.org/s2");
      Node s3 = NodeFactory.createURI("http://example.org/s3");
      Node predicate = NodeFactory.createURI("http://example.org/p");
      Node mainObj = NodeFactory.createLiteralString("main-only");
      Node featureObj = NodeFactory.createLiteralString("feature-only");

      assertThat(mainGraph.getDefaultGraph().contains(s2, predicate, mainObj)).isTrue();
      assertThat(mainGraph.getDefaultGraph().contains(s3, predicate, featureObj)).isFalse();
    } finally {
      mainGraph.end();
    }

    // Assert: Feature branch has feature-only data
    DatasetGraph featureGraph = repository.getBranchGraph("dataset1", "feature");
    featureGraph.begin(ReadWrite.READ);
    try {
      Node s2 = NodeFactory.createURI("http://example.org/s2");
      Node s3 = NodeFactory.createURI("http://example.org/s3");
      Node predicate = NodeFactory.createURI("http://example.org/p");
      Node mainObj = NodeFactory.createLiteralString("main-only");
      Node featureObj = NodeFactory.createLiteralString("feature-only");

      assertThat(featureGraph.getDefaultGraph().contains(s2, predicate, mainObj)).isFalse();
      assertThat(featureGraph.getDefaultGraph().contains(s3, predicate, featureObj)).isTrue();
    } finally {
      featureGraph.end();
    }
  }

  @Test
  void createBranch_shouldThrowExceptionIfParentDoesNotExist() {
    // Act & Assert
    assertThatThrownBy(() ->
        repository.createBranch("dataset1", "feature", Optional.of("nonexistent"))
    ).isInstanceOf(BranchNotFoundException.class)
        .hasMessageContaining("dataset1")
        .hasMessageContaining("nonexistent");
  }

  @Test
  void deleteBranch_shouldRemoveGraph() {
    // Arrange
    repository.getBranchGraph("dataset1", "main");
    assertThat(repository.exists("dataset1", "main")).isTrue();

    // Act
    repository.deleteBranch("dataset1", "main");

    // Assert
    assertThat(repository.exists("dataset1", "main")).isFalse();
    assertThat(repository.getGraphCount()).isZero();
  }

  @Test
  void deleteBranch_shouldBeIdempotent() {
    // Act: Delete non-existent branch
    repository.deleteBranch("dataset1", "nonexistent");

    // Assert: No exception thrown
    assertThat(repository.getGraphCount()).isZero();
  }

  @Test
  void exists_shouldReturnFalseForNonExistentBranch() {
    // Act & Assert
    assertThat(repository.exists("dataset1", "main")).isFalse();
  }

  @Test
  void exists_shouldReturnTrueAfterGraphCreation() {
    // Arrange
    repository.getBranchGraph("dataset1", "main");

    // Act & Assert
    assertThat(repository.exists("dataset1", "main")).isTrue();
  }

  @Test
  void getGraphCount_shouldReturnZeroInitially() {
    // Assert
    assertThat(repository.getGraphCount()).isZero();
  }

  @Test
  void getGraphCount_shouldReturnCorrectCount() {
    // Arrange
    repository.getBranchGraph("dataset1", "main");
    repository.getBranchGraph("dataset1", "feature");
    repository.getBranchGraph("dataset2", "main");

    // Assert
    assertThat(repository.getGraphCount()).isEqualTo(3);
  }

  @Test
  void getGraphCount_shouldDecrementAfterDeletion() {
    // Arrange
    repository.getBranchGraph("dataset1", "main");
    repository.getBranchGraph("dataset1", "feature");
    assertThat(repository.getGraphCount()).isEqualTo(2);

    // Act
    repository.deleteBranch("dataset1", "feature");

    // Assert
    assertThat(repository.getGraphCount()).isEqualTo(1);
  }

  /**
   * Parse RDF patch from string.
   *
   * @param patchStr the patch string in RDFPatch format
   * @return parsed RDFPatch
   */
  private RDFPatch parsePatch(String patchStr) {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        patchStr.getBytes(StandardCharsets.UTF_8));
    return RDFPatchOps.read(inputStream);
  }
}
