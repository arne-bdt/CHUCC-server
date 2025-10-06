package org.chucc.vcserver.util;

import java.util.HashSet;
import java.util.Set;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdfpatch.RDFChanges;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;

/**
 * Utility class for working with RDF Patches.
 * Provides methods to apply patches to DatasetGraphs and generate patches from graph diffs.
 */
public final class RdfPatchUtil {

  private RdfPatchUtil() {
    // Utility class - prevent instantiation
  }

  /**
   * Applies an RDF Patch to a DatasetGraph.
   * Wraps jena-rdfpatch's RDFPatchOps.applyChange for convenience.
   *
   * @param datasetGraph the target DatasetGraph to apply changes to (modified in-place)
   * @param patch the RDF Patch to apply
   * @throws IllegalArgumentException if datasetGraph or patch is null
   */
  public static void apply(DatasetGraph datasetGraph, RDFPatch patch) {
    if (datasetGraph == null) {
      throw new IllegalArgumentException("DatasetGraph cannot be null");
    }
    if (patch == null) {
      throw new IllegalArgumentException("RDFPatch cannot be null");
    }

    RDFPatchOps.applyChange(datasetGraph, patch);
  }

  /**
   * Generates an RDF Patch representing the difference between two graphs.
   * Currently provides a basic stub implementation.
   *
   * <p>For a precise diff, consider implementing:
   * <ul>
   *   <li>Graph isomorphism detection</li>
   *   <li>Triple-by-triple comparison with proper blank node handling</li>
   *   <li>Optimized change detection algorithms</li>
   * </ul>
   *
   * @param sourceGraph the source (original) graph
   * @param targetGraph the target (modified) graph
   * @return an RDF Patch representing changes from source to target
   * @throws IllegalArgumentException if sourceGraph or targetGraph is null
   */
  public static RDFPatch diff(Graph sourceGraph, Graph targetGraph) {
    if (sourceGraph == null) {
      throw new IllegalArgumentException("Source graph cannot be null");
    }
    if (targetGraph == null) {
      throw new IllegalArgumentException("Target graph cannot be null");
    }

    // Create a collector to build the patch
    RDFChangesCollector collector = new RDFChangesCollector();
    RDFChanges changes = collector;

    // Start transaction
    changes.txnBegin();

    // Use helper method to diff the graphs
    diffGraph(sourceGraph, targetGraph, null, changes);

    // Commit transaction
    changes.txnCommit();

    return collector.getRDFPatch();
  }

  /**
   * Generates an RDF Patch representing the difference between two DatasetGraphs.
   * Compares both the default graph and all named graphs.
   *
   * @param sourceDataset the source (original) dataset graph
   * @param targetDataset the target (modified) dataset graph
   * @return an RDF Patch representing changes from source to target
   * @throws IllegalArgumentException if sourceDataset or targetDataset is null
   */
  public static RDFPatch diff(DatasetGraph sourceDataset, DatasetGraph targetDataset) {
    if (sourceDataset == null) {
      throw new IllegalArgumentException("Source dataset cannot be null");
    }
    if (targetDataset == null) {
      throw new IllegalArgumentException("Target dataset cannot be null");
    }

    // Create a collector to build the patch
    RDFChangesCollector collector = new RDFChangesCollector();
    RDFChanges changes = collector;

    // Start transaction
    changes.txnBegin();

    // Diff the default graph
    diffGraph(sourceDataset.getDefaultGraph(), targetDataset.getDefaultGraph(),
        null, changes);

    // Collect all named graph URIs from both datasets
    Set<Node> allGraphNames = new HashSet<>();
    sourceDataset.listGraphNodes().forEachRemaining(allGraphNames::add);
    targetDataset.listGraphNodes().forEachRemaining(allGraphNames::add);

    // Diff each named graph
    for (Node graphName : allGraphNames) {
      Graph sourceGraph = sourceDataset.getGraph(graphName);
      Graph targetGraph = targetDataset.getGraph(graphName);

      // Handle cases where graph exists in only one dataset
      if (sourceGraph == null) {
        sourceGraph = org.apache.jena.sparql.graph.GraphFactory.createDefaultGraph();
      }
      if (targetGraph == null) {
        targetGraph = org.apache.jena.sparql.graph.GraphFactory.createDefaultGraph();
      }

      diffGraph(sourceGraph, targetGraph, graphName, changes);
    }

    // Commit transaction
    changes.txnCommit();

    return collector.getRDFPatch();
  }

  /**
   * Checks if applying a patch to a dataset results in no semantic change.
   * Per SPARQL 1.2 Protocol §7: A no-op patch applies cleanly but yields no dataset change.
   *
   * @param patch the RDF Patch to check
   * @param baseDataset the dataset to apply the patch to
   * @return true if the patch is a no-op (produces no semantic change)
   * @throws IllegalArgumentException if patch or baseDataset is null
   */
  public static boolean isNoOp(RDFPatch patch, DatasetGraph baseDataset) {
    if (patch == null) {
      throw new IllegalArgumentException("RDFPatch cannot be null");
    }
    if (baseDataset == null) {
      throw new IllegalArgumentException("DatasetGraph cannot be null");
    }

    // Create a temporary copy of the base dataset
    DatasetGraph tempDataset = DatasetGraphFactory.create();

    // Copy all quads from base to temp
    baseDataset.find().forEachRemaining(tempDataset::add);

    // Apply the patch to the temporary dataset
    try {
      RDFPatchOps.applyChange(tempDataset, patch);
    } catch (Exception e) {
      // Patch doesn't apply cleanly - not a no-op
      return false;
    }

    // Compare the base and temp datasets for isomorphism
    return isIsomorphic(baseDataset, tempDataset);
  }

  /**
   * Checks if two dataset graphs are isomorphic (structurally equivalent).
   * Two datasets are isomorphic if they contain the same quads.
   *
   * @param g1 the first dataset graph
   * @param g2 the second dataset graph
   * @return true if the datasets are isomorphic
   */
  private static boolean isIsomorphic(DatasetGraph g1, DatasetGraph g2) {
    // Quick check: compare quad counts
    long count1 = g1.stream().count();
    long count2 = g2.stream().count();

    if (count1 != count2) {
      return false;
    }

    // Check that all quads in g1 exist in g2 and vice versa
    return g1.stream().allMatch(g2::contains)
        && g2.stream().allMatch(g1::contains);
  }

  /**
   * Helper method to diff a single graph and add changes to the collector.
   *
   * @param sourceGraph the source graph
   * @param targetGraph the target graph
   * @param graphName the graph name (null for default graph)
   * @param changes the RDFChanges collector
   */
  private static void diffGraph(Graph sourceGraph, Graph targetGraph,
                                  Node graphName, RDFChanges changes) {
    // Find triples to delete (in source but not in target)
    sourceGraph.find().forEachRemaining(triple -> {
      if (!targetGraph.contains(triple)) {
        changes.delete(graphName, triple.getSubject(),
            triple.getPredicate(), triple.getObject());
      }
    });

    // Find triples to add (in target but not in source)
    targetGraph.find().forEachRemaining(triple -> {
      if (!sourceGraph.contains(triple)) {
        changes.add(graphName, triple.getSubject(),
            triple.getPredicate(), triple.getObject());
      }
    });
  }
}
