package org.chucc.vcserver.util;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.mem.DatasetGraphInMemory;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RdfPatchUtil.
 * Tests apply and diff operations with round-trip validation.
 */
class RdfPatchUtilTest {

  @Test
  void testApplyNullDatasetGraph() {
    RDFPatch emptyPatch = RdfPatchUtil.diff(GraphFactory.createDefaultGraph(),
        GraphFactory.createDefaultGraph());
    assertThrows(IllegalArgumentException.class, () -> {
      RdfPatchUtil.apply(null, emptyPatch);
    });
  }

  @Test
  void testApplyNullPatch() {
    DatasetGraph dsg = new DatasetGraphInMemory();
    assertThrows(IllegalArgumentException.class, () -> {
      RdfPatchUtil.apply(dsg, null);
    });
  }

  @Test
  void testDiffNullSourceGraph() {
    Graph target = GraphFactory.createDefaultGraph();
    assertThrows(IllegalArgumentException.class, () -> {
      RdfPatchUtil.diff(null, target);
    });
  }

  @Test
  void testDiffNullTargetGraph() {
    Graph source = GraphFactory.createDefaultGraph();
    assertThrows(IllegalArgumentException.class, () -> {
      RdfPatchUtil.diff(source, null);
    });
  }

  @Test
  void testDiffNullSourceDataset() {
    DatasetGraph target = new DatasetGraphInMemory();
    assertThrows(IllegalArgumentException.class, () -> {
      RdfPatchUtil.diff(null, target);
    });
  }

  @Test
  void testDiffNullTargetDataset() {
    DatasetGraph source = new DatasetGraphInMemory();
    assertThrows(IllegalArgumentException.class, () -> {
      RdfPatchUtil.diff(source, null);
    });
  }

  @Test
  void testRoundTripEmptyGraphs() {
    // Create empty graphs
    Graph sourceGraph = GraphFactory.createDefaultGraph();
    Graph targetGraph = GraphFactory.createDefaultGraph();

    // Generate diff
    RDFPatch patch = RdfPatchUtil.diff(sourceGraph, targetGraph);

    // Apply patch to a new graph
    DatasetGraph resultDataset = new DatasetGraphInMemory();
    RdfPatchUtil.apply(resultDataset, patch);

    // Verify result equals target
    Graph resultGraph = resultDataset.getDefaultGraph();
    assertTrue(resultGraph.isEmpty(), "Result graph should be empty");
    assertEquals(targetGraph.size(), resultGraph.size(), "Graph sizes should match");
  }

  @Test
  void testRoundTripAddTriples() {
    // Create source (empty) and target (with triples) graphs
    Graph sourceGraph = GraphFactory.createDefaultGraph();
    Graph targetGraph = GraphFactory.createDefaultGraph();

    // Add some triples to target
    targetGraph.add(createTriple("http://example.org/subject1",
        "http://example.org/predicate1",
        "http://example.org/object1"));
    targetGraph.add(createTriple("http://example.org/subject2",
        "http://example.org/predicate2",
        "http://example.org/object2"));

    // Generate diff patch
    RDFPatch patch = RdfPatchUtil.diff(sourceGraph, targetGraph);

    // Apply patch to a new dataset starting from source
    DatasetGraph resultDataset = new DatasetGraphInMemory();
    copyGraphToDataset(sourceGraph, resultDataset);
    RdfPatchUtil.apply(resultDataset, patch);

    // Verify result equals target
    Graph resultGraph = resultDataset.getDefaultGraph();
    assertEquals(targetGraph.size(), resultGraph.size(),
        "Graph sizes should match after applying patch");
    assertTrue(containsAllTriples(targetGraph, resultGraph),
        "Result should contain all triples from target");
  }

  @Test
  void testRoundTripDeleteTriples() {
    // Create source (with triples) and target (empty) graphs
    Graph sourceGraph = GraphFactory.createDefaultGraph();
    sourceGraph.add(createTriple("http://example.org/subject1",
        "http://example.org/predicate1",
        "http://example.org/object1"));
    sourceGraph.add(createTriple("http://example.org/subject2",
        "http://example.org/predicate2",
        "http://example.org/object2"));

    Graph targetGraph = GraphFactory.createDefaultGraph();

    // Generate diff patch
    RDFPatch patch = RdfPatchUtil.diff(sourceGraph, targetGraph);

    // Apply patch to a new dataset starting from source
    DatasetGraph resultDataset = new DatasetGraphInMemory();
    copyGraphToDataset(sourceGraph, resultDataset);
    RdfPatchUtil.apply(resultDataset, patch);

    // Verify result equals target (should be empty)
    Graph resultGraph = resultDataset.getDefaultGraph();
    assertTrue(resultGraph.isEmpty(), "Result graph should be empty after deleting all triples");
    assertEquals(targetGraph.size(), resultGraph.size(), "Graph sizes should match");
  }

  @Test
  void testRoundTripMixedChanges() {
    // Create source graph with some triples
    Graph sourceGraph = GraphFactory.createDefaultGraph();
    sourceGraph.add(createTriple("http://example.org/subject1",
        "http://example.org/predicate1",
        "http://example.org/object1"));
    sourceGraph.add(createTriple("http://example.org/subject2",
        "http://example.org/predicate2",
        "http://example.org/object2"));

    // Create target graph with different triples (some added, some removed)
    Graph targetGraph = GraphFactory.createDefaultGraph();
    targetGraph.add(createTriple("http://example.org/subject1",
        "http://example.org/predicate1",
        "http://example.org/object1")); // Kept
    targetGraph.add(createTriple("http://example.org/subject3",
        "http://example.org/predicate3",
        "http://example.org/object3")); // Added

    // Generate diff patch
    RDFPatch patch = RdfPatchUtil.diff(sourceGraph, targetGraph);

    // Apply patch to a new dataset starting from source
    DatasetGraph resultDataset = new DatasetGraphInMemory();
    copyGraphToDataset(sourceGraph, resultDataset);
    RdfPatchUtil.apply(resultDataset, patch);

    // Verify result equals target
    Graph resultGraph = resultDataset.getDefaultGraph();
    assertEquals(targetGraph.size(), resultGraph.size(),
        "Graph sizes should match after applying patch");
    assertTrue(containsAllTriples(targetGraph, resultGraph),
        "Result should contain all triples from target");
    assertFalse(resultGraph.contains(createTriple("http://example.org/subject2",
        "http://example.org/predicate2",
        "http://example.org/object2")),
        "Deleted triple should not be in result");
  }

  @Test
  void testRoundTripDatasetGraph() {
    // Create source and target dataset graphs
    DatasetGraph sourceDataset = new DatasetGraphInMemory();
    sourceDataset.getDefaultGraph().add(createTriple("http://example.org/subject1",
        "http://example.org/predicate1",
        "http://example.org/object1"));

    DatasetGraph targetDataset = new DatasetGraphInMemory();
    targetDataset.getDefaultGraph().add(createTriple("http://example.org/subject2",
        "http://example.org/predicate2",
        "http://example.org/object2"));

    // Generate diff patch
    RDFPatch patch = RdfPatchUtil.diff(sourceDataset, targetDataset);

    // Apply patch to a new dataset starting from source
    DatasetGraph resultDataset = new DatasetGraphInMemory();
    copyGraphToDataset(sourceDataset.getDefaultGraph(), resultDataset);
    RdfPatchUtil.apply(resultDataset, patch);

    // Verify result equals target
    Graph resultGraph = resultDataset.getDefaultGraph();
    assertEquals(targetDataset.getDefaultGraph().size(), resultGraph.size(),
        "Graph sizes should match after applying patch");
    assertTrue(containsAllTriples(targetDataset.getDefaultGraph(), resultGraph),
        "Result should contain all triples from target");
  }

  @Test
  void testRoundTripNamedGraphsAddGraph() {
    // Create source dataset with default graph only
    DatasetGraph sourceDataset = new DatasetGraphInMemory();
    sourceDataset.getDefaultGraph().add(createTriple("http://example.org/subject1",
        "http://example.org/predicate1",
        "http://example.org/object1"));

    // Create target dataset with default graph and a named graph
    DatasetGraph targetDataset = new DatasetGraphInMemory();
    targetDataset.getDefaultGraph().add(createTriple("http://example.org/subject1",
        "http://example.org/predicate1",
        "http://example.org/object1"));

    // Add a named graph to target
    Node graphName = NodeFactory.createURI("http://example.org/graph1");
    Graph namedGraph = targetDataset.getGraph(graphName);
    namedGraph.add(createTriple("http://example.org/ng-subject1",
        "http://example.org/ng-predicate1",
        "http://example.org/ng-object1"));

    // Generate diff patch
    RDFPatch patch = RdfPatchUtil.diff(sourceDataset, targetDataset);

    // Apply patch to a copy of source
    DatasetGraph resultDataset = new DatasetGraphInMemory();
    copyDataset(sourceDataset, resultDataset);
    RdfPatchUtil.apply(resultDataset, patch);

    // Verify result equals target
    assertEquals(targetDataset.getDefaultGraph().size(),
        resultDataset.getDefaultGraph().size(),
        "Default graph sizes should match");
    assertTrue(containsAllTriples(targetDataset.getDefaultGraph(),
        resultDataset.getDefaultGraph()),
        "Default graph should match");

    // Verify named graph exists and has correct content
    Graph resultNamedGraph = resultDataset.getGraph(graphName);
    assertEquals(1, resultNamedGraph.size(),
        "Named graph should have 1 triple");
    assertTrue(resultNamedGraph.contains(createTriple("http://example.org/ng-subject1",
        "http://example.org/ng-predicate1",
        "http://example.org/ng-object1")),
        "Named graph should contain the expected triple");
  }

  @Test
  void testRoundTripNamedGraphsRemoveGraph() {
    // Create source dataset with default graph and a named graph
    DatasetGraph sourceDataset = new DatasetGraphInMemory();
    sourceDataset.getDefaultGraph().add(createTriple("http://example.org/subject1",
        "http://example.org/predicate1",
        "http://example.org/object1"));

    Node graphName = NodeFactory.createURI("http://example.org/graph1");
    Graph sourceNamedGraph = sourceDataset.getGraph(graphName);
    sourceNamedGraph.add(createTriple("http://example.org/ng-subject1",
        "http://example.org/ng-predicate1",
        "http://example.org/ng-object1"));

    // Create target dataset with default graph only (named graph removed)
    DatasetGraph targetDataset = new DatasetGraphInMemory();
    targetDataset.getDefaultGraph().add(createTriple("http://example.org/subject1",
        "http://example.org/predicate1",
        "http://example.org/object1"));

    // Generate diff patch
    RDFPatch patch = RdfPatchUtil.diff(sourceDataset, targetDataset);

    // Apply patch to a copy of source
    DatasetGraph resultDataset = new DatasetGraphInMemory();
    copyDataset(sourceDataset, resultDataset);
    RdfPatchUtil.apply(resultDataset, patch);

    // Verify result equals target
    assertEquals(targetDataset.getDefaultGraph().size(),
        resultDataset.getDefaultGraph().size(),
        "Default graph sizes should match");

    // Verify named graph is empty (effectively removed)
    Graph resultNamedGraph = resultDataset.getGraph(graphName);
    assertTrue(resultNamedGraph.isEmpty(),
        "Named graph should be empty after removal");
  }

  @Test
  void testRoundTripNamedGraphsModifyGraph() {
    // Create source dataset with named graph
    DatasetGraph sourceDataset = new DatasetGraphInMemory();
    Node graphName = NodeFactory.createURI("http://example.org/graph1");
    Graph sourceNamedGraph = sourceDataset.getGraph(graphName);
    sourceNamedGraph.add(createTriple("http://example.org/subject1",
        "http://example.org/predicate1",
        "http://example.org/object1"));
    sourceNamedGraph.add(createTriple("http://example.org/subject2",
        "http://example.org/predicate2",
        "http://example.org/object2"));

    // Create target dataset with modified named graph
    DatasetGraph targetDataset = new DatasetGraphInMemory();
    Graph targetNamedGraph = targetDataset.getGraph(graphName);
    targetNamedGraph.add(createTriple("http://example.org/subject1",
        "http://example.org/predicate1",
        "http://example.org/object1")); // Kept
    targetNamedGraph.add(createTriple("http://example.org/subject3",
        "http://example.org/predicate3",
        "http://example.org/object3")); // Added (subject2 removed)

    // Generate diff patch
    RDFPatch patch = RdfPatchUtil.diff(sourceDataset, targetDataset);

    // Apply patch to a copy of source
    DatasetGraph resultDataset = new DatasetGraphInMemory();
    copyDataset(sourceDataset, resultDataset);
    RdfPatchUtil.apply(resultDataset, patch);

    // Verify result equals target
    Graph resultNamedGraph = resultDataset.getGraph(graphName);
    assertEquals(targetNamedGraph.size(), resultNamedGraph.size(),
        "Named graph sizes should match");
    assertTrue(containsAllTriples(targetNamedGraph, resultNamedGraph),
        "Named graph should contain all target triples");
    assertFalse(resultNamedGraph.contains(createTriple("http://example.org/subject2",
        "http://example.org/predicate2",
        "http://example.org/object2")),
        "Named graph should not contain deleted triple");
  }

  @Test
  void testRoundTripMultipleNamedGraphs() {
    // Create source dataset with multiple named graphs
    DatasetGraph sourceDataset = new DatasetGraphInMemory();
    Node graph1 = NodeFactory.createURI("http://example.org/graph1");
    Node graph2 = NodeFactory.createURI("http://example.org/graph2");

    sourceDataset.getGraph(graph1).add(createTriple("http://example.org/s1",
        "http://example.org/p1", "http://example.org/o1"));
    sourceDataset.getGraph(graph2).add(createTriple("http://example.org/s2",
        "http://example.org/p2", "http://example.org/o2"));

    // Create target dataset with different content in named graphs
    DatasetGraph targetDataset = new DatasetGraphInMemory();
    Node graph3 = NodeFactory.createURI("http://example.org/graph3");

    targetDataset.getGraph(graph1).add(createTriple("http://example.org/s1-modified",
        "http://example.org/p1", "http://example.org/o1"));
    targetDataset.getGraph(graph3).add(createTriple("http://example.org/s3",
        "http://example.org/p3", "http://example.org/o3"));
    // graph2 removed, graph3 added, graph1 modified

    // Generate diff patch
    RDFPatch patch = RdfPatchUtil.diff(sourceDataset, targetDataset);

    // Apply patch to a copy of source
    DatasetGraph resultDataset = new DatasetGraphInMemory();
    copyDataset(sourceDataset, resultDataset);
    RdfPatchUtil.apply(resultDataset, patch);

    // Verify graph1 is modified
    Graph resultGraph1 = resultDataset.getGraph(graph1);
    assertEquals(1, resultGraph1.size(), "Graph1 should have 1 triple");
    assertTrue(resultGraph1.contains(createTriple("http://example.org/s1-modified",
        "http://example.org/p1", "http://example.org/o1")),
        "Graph1 should contain modified triple");

    // Verify graph2 is empty (removed)
    Graph resultGraph2 = resultDataset.getGraph(graph2);
    assertTrue(resultGraph2.isEmpty(), "Graph2 should be empty");

    // Verify graph3 is added
    Graph resultGraph3 = resultDataset.getGraph(graph3);
    assertEquals(1, resultGraph3.size(), "Graph3 should have 1 triple");
    assertTrue(resultGraph3.contains(createTriple("http://example.org/s3",
        "http://example.org/p3", "http://example.org/o3")),
        "Graph3 should contain new triple");
  }

  // Helper methods

  private Triple createTriple(String subject, String predicate, String object) {
    return Triple.create(
        NodeFactory.createURI(subject),
        NodeFactory.createURI(predicate),
        NodeFactory.createURI(object)
    );
  }

  private void copyGraphToDataset(Graph source, DatasetGraph target) {
    source.find().forEachRemaining(triple -> target.getDefaultGraph().add(triple));
  }

  private void copyDataset(DatasetGraph source, DatasetGraph target) {
    // Copy default graph
    source.getDefaultGraph().find()
        .forEachRemaining(triple -> target.getDefaultGraph().add(triple));

    // Copy all named graphs
    source.listGraphNodes().forEachRemaining(graphName -> {
      Graph sourceGraph = source.getGraph(graphName);
      Graph targetGraph = target.getGraph(graphName);
      sourceGraph.find().forEachRemaining(targetGraph::add);
    });
  }

  private boolean containsAllTriples(Graph expected, Graph actual) {
    return expected.find().toList().stream().allMatch(actual::contains);
  }

  // ===== No-Op Patch Detection Tests =====

  @Test
  void testIsNoOpWithEmptyPatch() {
    // Create dataset with some data
    DatasetGraph dataset = new DatasetGraphInMemory();
    dataset.getDefaultGraph().add(createTriple("http://example.org/s",
        "http://example.org/p", "http://example.org/o"));

    // Create empty patch (no operations)
    RDFPatch emptyPatch = RdfPatchUtil.diff(
        GraphFactory.createDefaultGraph(),
        GraphFactory.createDefaultGraph());

    // Empty patch should be a no-op
    assertTrue(RdfPatchUtil.isNoOp(emptyPatch, dataset),
        "Empty patch should be a no-op");
  }

  @Test
  void testIsNoOpWithAddExistingTriple() {
    // Create dataset with existing data
    DatasetGraph dataset = new DatasetGraphInMemory();
    dataset.getDefaultGraph().add(createTriple("http://example.org/s",
        "http://example.org/p", "http://example.org/o"));

    // Create patch that adds the same triple
    Graph sourceGraph = GraphFactory.createDefaultGraph();
    Graph targetGraph = GraphFactory.createDefaultGraph();
    targetGraph.add(createTriple("http://example.org/s",
        "http://example.org/p", "http://example.org/o"));

    RDFPatch patch = RdfPatchUtil.diff(sourceGraph, targetGraph);

    // Adding existing triple should be a no-op
    assertTrue(RdfPatchUtil.isNoOp(patch, dataset),
        "Adding existing triple should be a no-op");
  }

  @Test
  void testIsNoOpWithDeleteNonExistentTriple() {
    // Create dataset with some data
    DatasetGraph dataset = new DatasetGraphInMemory();
    dataset.getDefaultGraph().add(createTriple("http://example.org/s1",
        "http://example.org/p1", "http://example.org/o1"));

    // Create patch that deletes a non-existent triple
    Graph sourceGraph = GraphFactory.createDefaultGraph();
    sourceGraph.add(createTriple("http://example.org/s2",
        "http://example.org/p2", "http://example.org/o2"));
    Graph targetGraph = GraphFactory.createDefaultGraph();

    RDFPatch patch = RdfPatchUtil.diff(sourceGraph, targetGraph);

    // Deleting non-existent triple should be a no-op
    assertTrue(RdfPatchUtil.isNoOp(patch, dataset),
        "Deleting non-existent triple should be a no-op");
  }

  @Test
  void testIsNoOpWithSelfCancelingOperations() {
    // Create dataset with some data
    DatasetGraph dataset = new DatasetGraphInMemory();
    dataset.getDefaultGraph().add(createTriple("http://example.org/s1",
        "http://example.org/p1", "http://example.org/o1"));

    // Create patch that adds and then deletes the same triple
    // This is a self-canceling operation that results in no net change
    Graph sourceGraph = GraphFactory.createDefaultGraph();
    sourceGraph.add(createTriple("http://example.org/s1",
        "http://example.org/p1", "http://example.org/o1"));

    Graph targetGraph = GraphFactory.createDefaultGraph();
    targetGraph.add(createTriple("http://example.org/s1",
        "http://example.org/p1", "http://example.org/o1"));

    RDFPatch patch = RdfPatchUtil.diff(sourceGraph, targetGraph);

    // Self-canceling operations should be a no-op
    assertTrue(RdfPatchUtil.isNoOp(patch, dataset),
        "Self-canceling operations should be a no-op");
  }

  @Test
  void testIsNoOpReturnsFalseForRealChanges() {
    // Create dataset with some data
    DatasetGraph dataset = new DatasetGraphInMemory();
    dataset.getDefaultGraph().add(createTriple("http://example.org/s1",
        "http://example.org/p1", "http://example.org/o1"));

    // Create patch that adds a new triple
    Graph sourceGraph = GraphFactory.createDefaultGraph();
    Graph targetGraph = GraphFactory.createDefaultGraph();
    targetGraph.add(createTriple("http://example.org/s2",
        "http://example.org/p2", "http://example.org/o2"));

    RDFPatch patch = RdfPatchUtil.diff(sourceGraph, targetGraph);

    // Real change should NOT be a no-op
    assertFalse(RdfPatchUtil.isNoOp(patch, dataset),
        "Patch with real changes should not be a no-op");
  }

  @Test
  void testIsNoOpReturnsFalseForDeletion() {
    // Create dataset with some data
    DatasetGraph dataset = new DatasetGraphInMemory();
    dataset.getDefaultGraph().add(createTriple("http://example.org/s1",
        "http://example.org/p1", "http://example.org/o1"));

    // Create patch that deletes an existing triple
    Graph sourceGraph = GraphFactory.createDefaultGraph();
    sourceGraph.add(createTriple("http://example.org/s1",
        "http://example.org/p1", "http://example.org/o1"));
    Graph targetGraph = GraphFactory.createDefaultGraph();

    RDFPatch patch = RdfPatchUtil.diff(sourceGraph, targetGraph);

    // Real deletion should NOT be a no-op
    assertFalse(RdfPatchUtil.isNoOp(patch, dataset),
        "Patch that deletes existing triple should not be a no-op");
  }

  @Test
  void testIsNoOpWithNullPatch() {
    DatasetGraph dataset = new DatasetGraphInMemory();
    assertThrows(IllegalArgumentException.class, () -> {
      RdfPatchUtil.isNoOp(null, dataset);
    });
  }

  @Test
  void testIsNoOpWithNullDataset() {
    RDFPatch patch = RdfPatchUtil.diff(
        GraphFactory.createDefaultGraph(),
        GraphFactory.createDefaultGraph());
    assertThrows(IllegalArgumentException.class, () -> {
      RdfPatchUtil.isNoOp(patch, null);
    });
  }

  // ===== Count Operations Tests =====

  @Test
  void testCountOperationsWithEmptyPatch() {
    // Create empty patch (no operations)
    RDFPatch emptyPatch = RdfPatchUtil.diff(
        GraphFactory.createDefaultGraph(),
        GraphFactory.createDefaultGraph());

    int count = RdfPatchUtil.countOperations(emptyPatch);

    assertEquals(0, count, "Empty patch should have 0 operations");
  }

  @Test
  void testCountOperationsWithOnlyAdds() {
    // Create patch with only add operations
    Graph sourceGraph = GraphFactory.createDefaultGraph();
    Graph targetGraph = GraphFactory.createDefaultGraph();
    targetGraph.add(createTriple("http://example.org/s1",
        "http://example.org/p1", "http://example.org/o1"));
    targetGraph.add(createTriple("http://example.org/s2",
        "http://example.org/p2", "http://example.org/o2"));
    targetGraph.add(createTriple("http://example.org/s3",
        "http://example.org/p3", "http://example.org/o3"));

    RDFPatch patch = RdfPatchUtil.diff(sourceGraph, targetGraph);
    int count = RdfPatchUtil.countOperations(patch);

    assertEquals(3, count, "Patch with 3 adds should have 3 operations");
  }

  @Test
  void testCountOperationsWithOnlyDeletes() {
    // Create patch with only delete operations
    Graph sourceGraph = GraphFactory.createDefaultGraph();
    sourceGraph.add(createTriple("http://example.org/s1",
        "http://example.org/p1", "http://example.org/o1"));
    sourceGraph.add(createTriple("http://example.org/s2",
        "http://example.org/p2", "http://example.org/o2"));

    Graph targetGraph = GraphFactory.createDefaultGraph();

    RDFPatch patch = RdfPatchUtil.diff(sourceGraph, targetGraph);
    int count = RdfPatchUtil.countOperations(patch);

    assertEquals(2, count, "Patch with 2 deletes should have 2 operations");
  }

  @Test
  void testCountOperationsWithMixedAddsAndDeletes() {
    // Create patch with both adds and deletes
    Graph sourceGraph = GraphFactory.createDefaultGraph();
    sourceGraph.add(createTriple("http://example.org/s1",
        "http://example.org/p1", "http://example.org/o1"));
    sourceGraph.add(createTriple("http://example.org/s2",
        "http://example.org/p2", "http://example.org/o2"));

    Graph targetGraph = GraphFactory.createDefaultGraph();
    targetGraph.add(createTriple("http://example.org/s1",
        "http://example.org/p1", "http://example.org/o1")); // Kept (not counted)
    targetGraph.add(createTriple("http://example.org/s3",
        "http://example.org/p3", "http://example.org/o3")); // Added
    targetGraph.add(createTriple("http://example.org/s4",
        "http://example.org/p4", "http://example.org/o4")); // Added
    // s2 deleted

    RDFPatch patch = RdfPatchUtil.diff(sourceGraph, targetGraph);
    int count = RdfPatchUtil.countOperations(patch);

    assertEquals(3, count, "Patch with 2 adds + 1 delete should have 3 operations");
  }

  @Test
  void testCountOperationsWithNullPatch() {
    assertThrows(IllegalArgumentException.class, () -> {
      RdfPatchUtil.countOperations(null);
    }, "countOperations should throw IllegalArgumentException for null patch");
  }

  @Test
  void testApplyPrefixPatchUpdatesDatasetGraph() {
    // Create a patch with PA directive
    org.apache.jena.rdfpatch.changes.RDFChangesCollector collector =
        new org.apache.jena.rdfpatch.changes.RDFChangesCollector();
    collector.start();
    collector.addPrefix(null, "foaf", "http://xmlns.com/foaf/0.1/");
    collector.finish();
    RDFPatch patch = collector.getRDFPatch();

    // Apply patch to empty dataset
    DatasetGraph dataset = new DatasetGraphInMemory();
    RdfPatchUtil.apply(dataset, patch);

    // Verify prefix was actually applied to the dataset
    String foafIri = dataset.getDefaultGraph().getPrefixMapping().getNsPrefixURI("foaf");
    assertEquals("http://xmlns.com/foaf/0.1/", foafIri,
        "Prefix should be applied to dataset graph");
  }

  @Test
  void testPrefixConflictDetectionEndToEnd() {
    // Simulate the merge flow: apply patches, materialize, diff, detect conflicts

    // Base: empty dataset
    DatasetGraph base = new DatasetGraphInMemory();

    // Into: add foaf prefix with one IRI
    DatasetGraph into = new DatasetGraphInMemory();
    org.apache.jena.rdfpatch.changes.RDFChangesCollector intoCollector =
        new org.apache.jena.rdfpatch.changes.RDFChangesCollector();
    intoCollector.start();
    intoCollector.addPrefix(null, "foaf", "http://xmlns.com/foaf/0.1/");
    intoCollector.finish();
    RDFPatch intoPatch = intoCollector.getRDFPatch();
    RdfPatchUtil.apply(into, intoPatch);

    // From: add foaf prefix with different IRI
    DatasetGraph from = new DatasetGraphInMemory();
    org.apache.jena.rdfpatch.changes.RDFChangesCollector fromCollector =
        new org.apache.jena.rdfpatch.changes.RDFChangesCollector();
    fromCollector.start();
    fromCollector.addPrefix(null, "foaf", "http://example.org/my-foaf#");
    fromCollector.finish();
    RDFPatch fromPatch = fromCollector.getRDFPatch();
    RdfPatchUtil.apply(from, fromPatch);

    // Verify prefix mappings are actually there
    assertEquals("http://xmlns.com/foaf/0.1/",
        into.getDefaultGraph().getPrefixMapping().getNsPrefixURI("foaf"),
        "Into should have foaf prefix");
    assertEquals("http://example.org/my-foaf#",
        from.getDefaultGraph().getPrefixMapping().getNsPrefixURI("foaf"),
        "From should have foaf prefix");

    // Diff to get change patches
    RDFPatch baseToInto = RdfPatchUtil.diff(base, into);
    RDFPatch baseToFrom = RdfPatchUtil.diff(base, from);

    // Detect conflicts
    java.util.List<org.chucc.vcserver.dto.ConflictItem> conflicts =
        org.chucc.vcserver.util.MergeUtil.detectConflicts(baseToInto, baseToFrom);

    // Should detect prefix conflict
    assertEquals(1, conflicts.size(), "Should detect exactly one conflict");
    org.chucc.vcserver.dto.ConflictItem conflict = conflicts.get(0);
    assertTrue(conflict.getSubject().startsWith("PREFIX:"),
        "Conflict should be for a prefix");
    assertTrue(conflict.getSubject().contains("foaf"),
        "Conflict should be for foaf prefix");
  }

  @Test
  void testRDFChangesCollectorRecordsPrefixes() {
    // Minimal test to verify RDFChangesCollector records PA/PD directives
    org.apache.jena.rdfpatch.changes.RDFChangesCollector collector =
        new org.apache.jena.rdfpatch.changes.RDFChangesCollector();

    collector.start();
    collector.addPrefix(null, "foaf", "http://xmlns.com/foaf/0.1/");
    collector.finish();

    RDFPatch patch = collector.getRDFPatch();

    // Serialize patch to string
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    org.apache.jena.rdfpatch.RDFPatchOps.write(baos, patch);
    String patchString = baos.toString(java.nio.charset.StandardCharsets.UTF_8);

    assertTrue(patchString.contains("PA") || patchString.contains("foaf"),
        "Collector should record prefix. Actual: " + patchString);
  }

  @Test
  void testDiffWithPrefixChanges() {
    // Create source dataset with no prefixes
    DatasetGraph sourceDataset = new DatasetGraphInMemory();

    // Create target dataset with prefix mapping
    DatasetGraph targetDataset = new DatasetGraphInMemory();
    targetDataset.getDefaultGraph().getPrefixMapping()
        .setNsPrefix("foaf", "http://xmlns.com/foaf/0.1/");

    // Generate diff
    RDFPatch patch = RdfPatchUtil.diff(sourceDataset, targetDataset);

    // Serialize patch to string
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    org.apache.jena.rdfpatch.RDFPatchOps.write(baos, patch);
    String patchString = baos.toString(java.nio.charset.StandardCharsets.UTF_8);

    // Verify patch contains PA directive
    assertTrue(patchString.contains("PA"),
        "Patch should contain PA (Prefix Add) directive. Actual: " + patchString);
    assertTrue(patchString.contains("foaf"),
        "Patch should contain 'foaf' prefix");
    assertTrue(patchString.contains("http://xmlns.com/foaf/0.1/"),
        "Patch should contain foaf IRI");
  }

  @Test
  void testDiffWithPrefixDeletion() {
    // Create source dataset with prefix
    DatasetGraph sourceDataset = new DatasetGraphInMemory();
    sourceDataset.getDefaultGraph().getPrefixMapping()
        .setNsPrefix("foaf", "http://xmlns.com/foaf/0.1/");

    // Create target dataset with no prefixes
    DatasetGraph targetDataset = new DatasetGraphInMemory();

    // Generate diff
    RDFPatch patch = RdfPatchUtil.diff(sourceDataset, targetDataset);

    // Serialize patch to string
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    org.apache.jena.rdfpatch.RDFPatchOps.write(baos, patch);
    String patchString = baos.toString(java.nio.charset.StandardCharsets.UTF_8);

    // Verify patch contains PD directive
    assertTrue(patchString.contains("PD"),
        "Patch should contain PD (Prefix Delete) directive. Actual: " + patchString);
    assertTrue(patchString.contains("foaf"),
        "Patch should contain 'foaf' prefix");
  }

  @Test
  void testDiffWithPrefixModification() {
    // Create source dataset with one prefix IRI
    DatasetGraph sourceDataset = new DatasetGraphInMemory();
    sourceDataset.getDefaultGraph().getPrefixMapping()
        .setNsPrefix("foaf", "http://xmlns.com/foaf/0.1/");

    // Create target dataset with same prefix but different IRI
    DatasetGraph targetDataset = new DatasetGraphInMemory();
    targetDataset.getDefaultGraph().getPrefixMapping()
        .setNsPrefix("foaf", "http://example.org/my-foaf#");

    // Generate diff
    RDFPatch patch = RdfPatchUtil.diff(sourceDataset, targetDataset);

    // Serialize patch to string
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    org.apache.jena.rdfpatch.RDFPatchOps.write(baos, patch);
    String patchString = baos.toString(java.nio.charset.StandardCharsets.UTF_8);

    // Verify patch contains both PD and PA (delete old, add new)
    assertTrue(patchString.contains("PD"),
        "Patch should contain PD (Prefix Delete) directive for old IRI. Actual: " + patchString);
    assertTrue(patchString.contains("PA"),
        "Patch should contain PA (Prefix Add) directive for new IRI");
    assertTrue(patchString.contains("http://example.org/my-foaf#"),
        "Patch should contain new IRI");
  }
}
