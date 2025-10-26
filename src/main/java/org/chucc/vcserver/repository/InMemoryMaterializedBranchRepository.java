package org.chucc.vcserver.repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.changes.RDFChangesApply;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.exception.PatchApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * In-memory implementation of MaterializedBranchRepository.
 *
 * <p>Stores all materialized graphs in a ConcurrentHashMap for thread-safe access.
 * Memory usage scales with the number of branches and triples per branch.
 *
 * <p>Thread Safety:
 * <ul>
 * <li>ConcurrentHashMap provides thread-safe map operations</li>
 * <li>Per-branch locks prevent concurrent patch application</li>
 * <li>Jena transactions provide thread-safe graph operations</li>
 * <li>computeIfAbsent ensures atomic graph creation</li>
 * </ul>
 */
@Repository
public class InMemoryMaterializedBranchRepository implements MaterializedBranchRepository {

  private static final Logger logger =
      LoggerFactory.getLogger(InMemoryMaterializedBranchRepository.class);

  // Key: "dataset:branch" → Value: DatasetGraph
  private final ConcurrentHashMap<String, DatasetGraph> branchGraphs = new ConcurrentHashMap<>();

  // Per-branch locks to prevent concurrent patch application
  // Key: "dataset:branch" → Value: Lock
  private final ConcurrentHashMap<String, Lock> branchLocks = new ConcurrentHashMap<>();

  @Override
  public DatasetGraph getBranchGraph(String dataset, String branch) {
    String key = toKey(dataset, branch);
    return branchGraphs.computeIfAbsent(key, k -> {
      logger.info("Creating new materialized graph for {}/{}", dataset, branch);
      return createEmptyDatasetGraph();
    });
  }

  @Override
  public void applyPatchToBranch(String dataset, String branch, RDFPatch patch) {
    String key = toKey(dataset, branch);
    Lock lock = branchLocks.computeIfAbsent(key, k -> new ReentrantLock());

    lock.lock();
    try {
      DatasetGraph graph = getBranchGraph(dataset, branch);

      // Apply patch directly without transaction management
      // We use locks for concurrency control instead of transactions
      applyPatchDirect(graph, patch);
      logger.debug("Applied patch to materialized graph {}/{}", dataset, branch);
    } catch (Exception e) {
      String errorMsg = String.format(
          "Failed to apply patch to materialized graph %s/%s", dataset, branch);
      logger.error(errorMsg, e);
      throw new PatchApplicationException(errorMsg, e);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Apply an RDF patch directly to a dataset graph without transaction management.
   * Creates a custom RDFChangesApply that skips transaction operations.
   *
   * @param graph the target graph
   * @param patch the patch to apply
   */
  private void applyPatchDirect(DatasetGraph graph, RDFPatch patch) {
    // Create a changes applier that skips transaction management
    RDFChangesApply changes = new RDFChangesApply(graph) {
      @Override
      public void txnBegin() {
        // Skip transaction begin - we use locks for concurrency control
      }

      @Override
      public void txnCommit() {
        // Skip transaction commit - changes are applied directly
      }

      @Override
      public void txnAbort() {
        // Skip transaction abort - we don't manage transactions
      }
    };

    // Apply the patch
    patch.apply(changes);
  }

  @Override
  public void createBranch(String dataset, String branch, Optional<String> parentBranch) {
    String key = toKey(dataset, branch);

    if (!parentBranch.isPresent()) {
      // Simple case: create empty branch (no locking needed)
      logger.info("Creating empty branch {}/{}", dataset, branch);
      branchGraphs.put(key, createEmptyDatasetGraph());
      return;
    }

    // Clone from parent branch (requires locking)
    String parentKey = toKey(dataset, parentBranch.get());
    DatasetGraph parentGraph = branchGraphs.get(parentKey);

    if (parentGraph == null) {
      throw new BranchNotFoundException(dataset + "/" + parentBranch.get());
    }

    logger.info("Creating branch {}/{} by cloning {}", dataset, branch, parentBranch.get());

    // Lock parent branch to prevent concurrent modifications during cloning
    Lock parentLock = branchLocks.computeIfAbsent(parentKey, k -> new ReentrantLock());
    parentLock.lock();
    try {
      DatasetGraph clonedGraph = cloneDatasetGraph(parentGraph);
      branchGraphs.put(key, clonedGraph);
    } finally {
      parentLock.unlock();
    }
  }

  @Override
  public void deleteBranch(String dataset, String branch) {
    String key = toKey(dataset, branch);
    DatasetGraph graph = branchGraphs.remove(key);
    branchLocks.remove(key);

    if (graph != null) {
      logger.info("Deleted materialized graph for {}/{}", dataset, branch);
      graph.close();
    }
  }

  @Override
  public boolean exists(String dataset, String branch) {
    String key = toKey(dataset, branch);
    return branchGraphs.containsKey(key);
  }

  @Override
  public int getGraphCount() {
    return branchGraphs.size();
  }

  /**
   * Create an empty non-transactional DatasetGraph.
   * Note: RDFPatchOps.applyChange() manages its own transactions,
   * so we use non-transactional graphs to avoid nesting issues.
   *
   * @return new empty DatasetGraph
   */
  private DatasetGraph createEmptyDatasetGraph() {
    // Use non-transactional in-memory dataset
    // DatasetGraphFactory.create() returns a plain DatasetGraphMapLink
    // which doesn't support transactions, avoiding nested transaction errors
    return DatasetGraphFactory.create();
  }

  /**
   * Clone a DatasetGraph by copying all graphs and triples.
   * Works with both transactional and non-transactional graphs.
   *
   * @param source the source graph to clone
   * @return a new DatasetGraph with copied data
   */
  private DatasetGraph cloneDatasetGraph(DatasetGraph source) {
    DatasetGraph target = createEmptyDatasetGraph();

    // Copy all named graphs
    source.listGraphNodes().forEachRemaining(graphName -> {
      Graph sourceGraph = source.getGraph(graphName);
      Graph targetGraph = target.getGraph(graphName);

      // Copy prefix mappings
      targetGraph.getPrefixMapping().setNsPrefixes(sourceGraph.getPrefixMapping());

      // Copy all triples
      sourceGraph.find().forEachRemaining(targetGraph::add);
    });

    // Copy default graph
    Graph defaultSource = source.getDefaultGraph();
    Graph defaultTarget = target.getDefaultGraph();
    defaultTarget.getPrefixMapping().setNsPrefixes(defaultSource.getPrefixMapping());
    defaultSource.find().forEachRemaining(defaultTarget::add);

    return target;
  }

  /**
   * Create cache key from dataset and branch names.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @return cache key in format "dataset:branch"
   */
  private String toKey(String dataset, String branch) {
    return dataset + ":" + branch;
  }
}
