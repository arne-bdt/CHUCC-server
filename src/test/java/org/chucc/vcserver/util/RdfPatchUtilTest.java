package org.chucc.vcserver.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.mem.DatasetGraphInMemory;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.Test;

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
}
