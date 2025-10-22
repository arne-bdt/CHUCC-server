package org.chucc.vcserver.service;

import java.io.ByteArrayOutputStream;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.springframework.stereotype.Service;

/**
 * Service for computing RDF diffs between graphs.
 * Used for PUT operations to generate patches representing graph replacements.
 */
@Service
public class GraphDiffService {

  /**
   * Computes an RDF Patch for PUT operation (replace entire graph).
   * Generates DELETE operations for all quads in oldGraph,
   * then ADD operations for all quads in newGraph.
   *
   * @param oldGraph the current graph state
   * @param newGraph the desired graph state
   * @param graphIri the graph IRI (null for default graph)
   * @return the RDF Patch representing the replacement
   */
  public RDFPatch computePutDiff(Graph oldGraph, Graph newGraph, String graphIri) {
    RDFChangesCollector collector = new RDFChangesCollector();

    // Start transaction
    collector.txnBegin();

    Node graphNode = graphIri != null
        ? NodeFactory.createURI(graphIri)
        : org.apache.jena.sparql.core.Quad.defaultGraphNodeGenerated;

    // Delete triples that are in old but not in new
    oldGraph.find().forEachRemaining(triple -> {
      if (!newGraph.contains(triple)) {
        collector.delete(
            graphNode,
            triple.getSubject(),
            triple.getPredicate(),
            triple.getObject()
        );
      }
    });

    // Add triples that are in new but not in old
    newGraph.find().forEachRemaining(triple -> {
      if (!oldGraph.contains(triple)) {
        collector.add(
            graphNode,
            triple.getSubject(),
            triple.getPredicate(),
            triple.getObject()
        );
      }
    });

    // Commit transaction
    collector.txnCommit();

    return collector.getRDFPatch();
  }

  /**
   * Computes an RDF Patch for POST operation (additive merge).
   * Generates only ADD operations for triples in newContent that are not in currentGraph.
   *
   * @param currentGraph the current graph state
   * @param newContent the RDF content to merge
   * @param graphIri the graph IRI (null for default graph)
   * @return the RDF Patch representing the additive merge
   */
  public RDFPatch computePostDiff(Graph currentGraph, Graph newContent, String graphIri) {
    RDFChangesCollector collector = new RDFChangesCollector();

    // Start transaction
    collector.txnBegin();

    Node graphNode = graphIri != null
        ? NodeFactory.createURI(graphIri)
        : org.apache.jena.sparql.core.Quad.defaultGraphNodeGenerated;

    // Add only triples that are in newContent but not in currentGraph
    newContent.find().forEachRemaining(triple -> {
      if (!currentGraph.contains(triple)) {
        collector.add(
            graphNode,
            triple.getSubject(),
            triple.getPredicate(),
            triple.getObject()
        );
      }
    });

    // Commit transaction
    collector.txnCommit();

    return collector.getRDFPatch();
  }

  /**
   * Computes an RDF Patch for DELETE operation (remove entire graph).
   * Generates only DELETE operations for all triples in currentGraph.
   *
   * @param currentGraph the current graph state to delete
   * @param graphIri the graph IRI (null for default graph)
   * @return the RDF Patch representing the deletion
   */
  public RDFPatch computeDeleteDiff(Graph currentGraph, String graphIri) {
    RDFChangesCollector collector = new RDFChangesCollector();

    // Start transaction
    collector.txnBegin();

    Node graphNode = graphIri != null
        ? NodeFactory.createURI(graphIri)
        : org.apache.jena.sparql.core.Quad.defaultGraphNodeGenerated;

    // Delete all triples in the graph
    currentGraph.find().forEachRemaining(triple -> {
      collector.delete(
          graphNode,
          triple.getSubject(),
          triple.getPredicate(),
          triple.getObject()
      );
    });

    // Commit transaction
    collector.txnCommit();

    return collector.getRDFPatch();
  }

  /**
   * Checks if an RDF Patch is empty (has no add/delete operations).
   *
   * @param patch the patch to check
   * @return true if the patch has no operations, false otherwise
   */
  public boolean isPatchEmpty(RDFPatch patch) {
    // Serialize to string and check for operations
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    RDFPatchOps.write(out, patch);
    String patchString = out.toString(java.nio.charset.StandardCharsets.UTF_8);

    // Check if patch contains any add/delete operations
    // A non-empty patch will have lines starting with 'A' (add) or 'D' (delete)
    return !patchString.lines()
        .anyMatch(line -> line.startsWith("A ") || line.startsWith("D "));
  }
}
