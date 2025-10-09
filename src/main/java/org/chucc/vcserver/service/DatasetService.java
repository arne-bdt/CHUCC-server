package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.mem.DatasetGraphInMemory;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.DatasetRef;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.springframework.stereotype.Service;

/**
 * Service for managing Jena in-memory datasets with version control.
 * Provides datasets for specific branches or commit snapshots.
 * Thread-safe for concurrent read operations.
 *
 * <p>Performance: Uses a simple ConcurrentHashMap cache for materialized datasets.
 * In-memory RDF patch application is fast, so sophisticated caching is deferred
 * until performance data indicates it's needed.
 */
@Service
public class DatasetService {
  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;

  // Cache of materialized dataset graphs per commit
  // Simple ConcurrentHashMap is sufficient for current needs
  private final Map<String, Map<CommitId, DatasetGraphInMemory>> datasetCache =
      new ConcurrentHashMap<>();

  /**
   * Creates a new DatasetService with observability support.
   *
   * @param branchRepository the branch repository
   * @param commitRepository the commit repository
   * @param meterRegistry the meter registry for metrics
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared")
  public DatasetService(BranchRepository branchRepository, CommitRepository commitRepository,
      MeterRegistry meterRegistry) {
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;

    // Register cache size gauge for observability
    Gauge.builder("dataset.cache.size", datasetCache, Map::size)
        .description("Number of datasets in the materialization cache")
        .register(meterRegistry);
  }

  /**
   * Gets a Jena Dataset for a given dataset reference.
   * Resolves branch references to their current HEAD commit.
   *
   * @param datasetRef the dataset reference (dataset name + ref)
   * @return a Dataset instance materialized to the specified version
   * @throws IllegalArgumentException if the reference cannot be resolved
   */
  public Dataset getDataset(DatasetRef datasetRef) {
    CommitId commitId = resolveRef(datasetRef);
    DatasetGraph datasetGraph = getOrCreateDatasetGraph(datasetRef.datasetName(), commitId);

    // Return a read-only view for safety
    return DatasetFactory.wrap(datasetGraph);
  }

  /**
   * Gets a mutable Dataset for a given dataset reference.
   * Used for write operations that will create new commits.
   *
   * @param datasetRef the dataset reference
   * @return a mutable Dataset instance
   * @throws IllegalArgumentException if the reference cannot be resolved
   */
  public Dataset getMutableDataset(DatasetRef datasetRef) {
    CommitId commitId = resolveRef(datasetRef);
    DatasetGraph datasetGraph = getOrCreateDatasetGraph(datasetRef.datasetName(), commitId);
    return DatasetFactory.wrap(datasetGraph);
  }

  /**
   * Creates a new dataset with an initial empty commit on the main branch.
   *
   * @param datasetName the dataset name
   * @param author the author of the initial commit
   * @return the created branch
   */
  public Branch createDataset(String datasetName, String author) {
    // Create initial empty commit
    Commit initialCommit = Commit.create(
        java.util.List.of(),
        author,
        "Initial commit"
    );

    // Create empty patch for initial commit
    RDFPatch emptyPatch = RDFPatchOps.emptyPatch();

    // Save commit and patch
    commitRepository.save(datasetName, initialCommit, emptyPatch);

    // Create main branch pointing to initial commit
    Branch mainBranch = new Branch("main", initialCommit.id());
    branchRepository.save(datasetName, mainBranch);

    // Initialize empty dataset graph for this commit
    DatasetGraphInMemory datasetGraph = new DatasetGraphInMemory();
    cacheDatasetGraph(datasetName, initialCommit.id(), datasetGraph);

    return mainBranch;
  }

  /**
   * Resolves a reference (branch name or commit ID) to a commit ID.
   *
   * @param datasetRef the dataset reference
   * @return the resolved commit ID
   * @throws IllegalArgumentException if the reference cannot be resolved
   */
  @SuppressWarnings("PMD.EmptyCatchBlock") // Exception used for control flow
  private CommitId resolveRef(DatasetRef datasetRef) {
    String ref = datasetRef.ref();

    // Try to parse as commit ID first
    try {
      CommitId commitId = CommitId.of(ref);
      if (commitRepository.exists(datasetRef.datasetName(), commitId)) {
        return commitId;
      }
    } catch (IllegalArgumentException e) {
      // Not a valid commit ID, try as branch name
    }

    // Try to resolve as branch name
    return branchRepository.findByDatasetAndName(datasetRef.datasetName(), ref)
        .map(Branch::getCommitId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Cannot resolve reference: " + ref + " in dataset: " + datasetRef.datasetName()));
  }

  /**
   * Gets or creates a DatasetGraphInMemory for a specific commit.
   * Uses caching to avoid rebuilding the same dataset multiple times.
   *
   * @param datasetName the dataset name
   * @param commitId the commit ID
   * @return the dataset graph materialized to the specified commit
   */
  private DatasetGraphInMemory getOrCreateDatasetGraph(String datasetName, CommitId commitId) {
    return datasetCache.computeIfAbsent(datasetName, k -> new ConcurrentHashMap<>())
        .computeIfAbsent(commitId, id -> buildDatasetGraph(datasetName, id));
  }

  /**
   * Builds a DatasetGraphInMemory by applying all patches from the commit history.
   *
   * @param datasetName the dataset name
   * @param commitId the target commit ID
   * @return the materialized dataset graph
   */
  private DatasetGraphInMemory buildDatasetGraph(String datasetName, CommitId commitId) {
    DatasetGraphInMemory datasetGraph = new DatasetGraphInMemory();

    // Get the commit
    Commit commit = commitRepository.findByDatasetAndId(datasetName, commitId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Commit not found: " + commitId + " in dataset: " + datasetName));

    // Build the graph by applying patches from commit history
    applyPatchHistory(datasetName, commit, datasetGraph);

    return datasetGraph;
  }

  /**
   * Recursively applies patches from commit history to build the dataset state.
   *
   * @param datasetName the dataset name
   * @param commit the current commit
   * @param datasetGraph the dataset graph to apply patches to
   */
  private void applyPatchHistory(String datasetName, Commit commit,
                                   DatasetGraphInMemory datasetGraph) {
    // First, recursively process parent commits
    for (CommitId parentId : commit.parents()) {
      Commit parentCommit = commitRepository.findByDatasetAndId(datasetName, parentId)
          .orElseThrow(() -> new IllegalArgumentException(
              "Parent commit not found: " + parentId));
      applyPatchHistory(datasetName, parentCommit, datasetGraph);
    }

    // Then apply this commit's patch
    commitRepository.findPatchByDatasetAndId(datasetName, commit.id())
        .ifPresent(patch -> RDFPatchOps.applyChange(datasetGraph, patch));
  }

  /**
   * Caches a dataset graph for a specific commit.
   *
   * @param datasetName the dataset name
   * @param commitId the commit ID
   * @param datasetGraph the dataset graph to cache
   */
  private void cacheDatasetGraph(String datasetName, CommitId commitId,
                                   DatasetGraphInMemory datasetGraph) {
    datasetCache.computeIfAbsent(datasetName, k -> new ConcurrentHashMap<>())
        .put(commitId, datasetGraph);
  }

  /**
   * Materializes a dataset graph at a specific commit.
   * This creates a snapshot of the dataset state at the given commit by applying
   * all patches in the commit history.
   *
   * @param datasetName the dataset name
   * @param commitId the commit ID to materialize
   * @return the dataset graph at the specified commit
   * @throws IllegalArgumentException if the commit is not found
   */
  public DatasetGraph materializeCommit(String datasetName, CommitId commitId) {
    return getOrCreateDatasetGraph(datasetName, commitId);
  }

  /**
   * Gets a named graph from a dataset at a specific commit.
   *
   * @param datasetName the dataset name
   * @param commitId the commit ID
   * @param graphIri the graph IRI
   * @return the named graph as a Model, or null if the graph doesn't exist
   * @throws IllegalArgumentException if the commit is not found
   */
  public org.apache.jena.rdf.model.Model getGraph(String datasetName, CommitId commitId,
      String graphIri) {
    DatasetGraph datasetGraph = materializeCommit(datasetName, commitId);
    org.apache.jena.graph.Node graphNode =
        org.apache.jena.graph.NodeFactory.createURI(graphIri);

    if (!datasetGraph.containsGraph(graphNode)) {
      return null;
    }

    org.apache.jena.graph.Graph graph = datasetGraph.getGraph(graphNode);
    return org.apache.jena.rdf.model.ModelFactory.createModelForGraph(graph);
  }

  /**
   * Gets the default graph from a dataset at a specific commit.
   *
   * @param datasetName the dataset name
   * @param commitId the commit ID
   * @return the default graph as a Model (never null, but may be empty)
   * @throws IllegalArgumentException if the commit is not found
   */
  public org.apache.jena.rdf.model.Model getDefaultGraph(String datasetName, CommitId commitId) {
    DatasetGraph datasetGraph = materializeCommit(datasetName, commitId);
    org.apache.jena.graph.Graph graph = datasetGraph.getDefaultGraph();
    return org.apache.jena.rdf.model.ModelFactory.createModelForGraph(graph);
  }

  /**
   * Finds the last commit that modified a specific graph.
   * Walks commit history backwards to find the most recent commit
   * that contains changes to the specified graph.
   *
   * @param datasetName the dataset name
   * @param startCommit the commit to start searching from (typically branch HEAD)
   * @param graphIri the graph IRI to search for (null for default graph)
   * @return the commit ID that last modified the graph, or the initial commit if never modified
   */
  public CommitId findLastModifyingCommit(
      String datasetName, CommitId startCommit, String graphIri) {
    // Walk backwards from startCommit
    CommitId currentCommitId = startCommit;

    while (currentCommitId != null) {
      // Get the patch for this commit
      java.util.Optional<RDFPatch> patchOpt =
          commitRepository.findPatchByDatasetAndId(datasetName, currentCommitId);

      if (patchOpt.isPresent()) {
        RDFPatch patch = patchOpt.get();

        // Check if this patch modifies the target graph
        if (patchModifiesGraph(patch, graphIri)) {
          return currentCommitId;
        }
      }

      // Move to parent commit
      java.util.Optional<Commit> commitOpt =
          commitRepository.findByDatasetAndId(datasetName, currentCommitId);

      if (commitOpt.isEmpty() || commitOpt.get().parents().isEmpty()) {
        // Reached initial commit or no parent found
        break;
      }

      // Follow first parent (mainline history)
      currentCommitId = commitOpt.get().parents().get(0);
    }

    // If no modifying commit found, return the start commit
    // (graph was never modified, so ETag is the current commit)
    return startCommit;
  }

  /**
   * Checks if an RDF Patch modifies a specific graph.
   *
   * @param patch the RDF patch to inspect
   * @param graphIri the graph IRI to check (null for default graph)
   * @return true if the patch contains operations on the specified graph
   */
  private boolean patchModifiesGraph(RDFPatch patch, String graphIri) {
    // Use a collector to check if any operations target the graph
    class GraphModificationChecker
        extends org.apache.jena.rdfpatch.changes.RDFChangesWrapper {
      private boolean modified = false;

      GraphModificationChecker() {
        super(new org.apache.jena.rdfpatch.changes.RDFChangesCollector());
      }

      private boolean graphMatches(org.apache.jena.graph.Node g) {
        // Check if g represents the default graph
        boolean isDefaultGraphNode = g == null
            || g.equals(org.apache.jena.sparql.core.Quad.defaultGraphNodeGenerated)
            || g.equals(org.apache.jena.sparql.core.Quad.defaultGraphIRI);

        if (graphIri == null) {
          // Checking default graph
          return isDefaultGraphNode;
        } else {
          // Checking named graph
          return isDefaultGraphNode || (g.isURI() && g.getURI().equals(graphIri));
        }
      }

      @Override
      public void add(org.apache.jena.graph.Node g, org.apache.jena.graph.Node s,
          org.apache.jena.graph.Node p, org.apache.jena.graph.Node o) {
        if (graphMatches(g)) {
          modified = true;
        }
        super.add(g, s, p, o);
      }

      @Override
      public void delete(org.apache.jena.graph.Node g, org.apache.jena.graph.Node s,
          org.apache.jena.graph.Node p, org.apache.jena.graph.Node o) {
        if (graphMatches(g)) {
          modified = true;
        }
        super.delete(g, s, p, o);
      }

      public boolean isModified() {
        return modified;
      }
    }

    GraphModificationChecker checker = new GraphModificationChecker();
    patch.apply(checker);
    return checker.isModified();
  }

  /**
   * Clears the dataset cache for a specific dataset.
   *
   * @param datasetName the dataset name
   */
  public void clearCache(String datasetName) {
    datasetCache.remove(datasetName);
  }

  /**
   * Clears all dataset caches.
   */
  public void clearAllCaches() {
    datasetCache.clear();
  }
}
