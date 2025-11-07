package org.chucc.vcserver.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesApply;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.mem.DatasetGraphInMemory;
import org.chucc.vcserver.config.CacheProperties;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.DatasetRef;
import org.chucc.vcserver.domain.Snapshot;
import org.chucc.vcserver.exception.CommitNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for managing Jena in-memory datasets with version control.
 * Provides datasets for specific branches or commit snapshots.
 * Thread-safe for concurrent read operations.
 *
 * <p>Performance: Uses Caffeine LRU cache for materialized datasets with configurable
 * eviction policies. Latest commits per branch are kept in cache to ensure fast queries.
 */
@Service
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class DatasetService {
  private static final Logger logger = LoggerFactory.getLogger(DatasetService.class);

  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;
  private final SnapshotService snapshotService;
  private final MaterializedBranchRepository materializedBranchRepo;

  // Caffeine LRU cache for historical commit graphs
  // Note: Branch HEADs are now served by MaterializedBranchRepository (no cache needed)
  private final Cache<CacheKey, DatasetGraphInMemory> datasetCache;

  /**
   * Cache key combining dataset name and commit ID.
   *
   * @param dataset the dataset name
   * @param commitId the commit ID
   */
  private record CacheKey(String dataset, CommitId commitId) {}

  /**
   * Creates a new DatasetService with observability support.
   *
   * @param branchRepository the branch repository
   * @param commitRepository the commit repository
   * @param snapshotService the snapshot service (lazily initialized to avoid circular dependency)
   * @param cacheProperties the cache configuration properties
   * @param meterRegistry the meter registry for metrics
   * @param materializedBranchRepo the materialized branch repository
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared")
  public DatasetService(BranchRepository branchRepository, CommitRepository commitRepository,
      @org.springframework.context.annotation.Lazy SnapshotService snapshotService,
      CacheProperties cacheProperties,
      MeterRegistry meterRegistry,
      MaterializedBranchRepository materializedBranchRepo) {
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
    this.snapshotService = snapshotService;
    this.materializedBranchRepo = materializedBranchRepo;

    // Build Caffeine cache with LRU eviction
    Caffeine<CacheKey, DatasetGraphInMemory> cacheBuilder = Caffeine.newBuilder()
        .maximumSize(cacheProperties.getMaxSize())
        .recordStats()  // Enable statistics for monitoring
        .removalListener(new CacheRemovalListener());

    // Optional TTL for time-based eviction
    if (cacheProperties.getTtlMinutes() > 0) {
      cacheBuilder.expireAfterWrite(cacheProperties.getTtlMinutes(), TimeUnit.MINUTES);
    }

    this.datasetCache = cacheBuilder.build();

    // Register cache metrics for observability
    registerCacheMetrics(meterRegistry);
  }

  /**
   * Listener for cache evictions (for logging and debugging).
   */
  private static final class CacheRemovalListener
      implements RemovalListener<CacheKey, DatasetGraphInMemory> {

    @Override
    public void onRemoval(CacheKey key, DatasetGraphInMemory graph, RemovalCause cause) {
      if (cause.wasEvicted()) {
        logger.debug("Evicted dataset graph from cache: {} at commit {} (reason: {})",
            key.dataset(), key.commitId(), cause);
      }
    }
  }

  /**
   * Registers cache metrics with Micrometer.
   *
   * @param meterRegistry the meter registry
   */
  private void registerCacheMetrics(MeterRegistry meterRegistry) {
    // Cache size
    Gauge.builder("dataset.cache.size", datasetCache, Cache::estimatedSize)
        .description("Number of cached dataset graphs")
        .register(meterRegistry);

    // Hit rate
    Gauge.builder("dataset.cache.hit.rate", datasetCache,
        cache -> cache.stats().hitRate())
        .description("Cache hit rate (0-1)")
        .register(meterRegistry);

    // Eviction count
    Gauge.builder("dataset.cache.evictions", datasetCache,
        cache -> cache.stats().evictionCount())
        .description("Total number of cache evictions")
        .register(meterRegistry);
  }

  /**
   * Gets a Jena Dataset for a given dataset reference.
   * Resolves branch references to their current HEAD commit.
   *
   * <p>Uses materialized graphs for branch HEAD queries (instant, O(1) lookup).
   * Falls back to on-demand building for commit queries or when materialized graph unavailable.
   *
   * @param datasetRef the dataset reference (dataset name + ref)
   * @return a Dataset instance materialized to the specified version
   * @throws IllegalArgumentException if the reference cannot be resolved
   */
  public Dataset getDataset(DatasetRef datasetRef) {
    DatasetGraph datasetGraph = getDatasetGraph(datasetRef);
    // Return a read-only view for safety
    return DatasetFactory.wrap(datasetGraph);
  }

  /**
   * Gets a mutable Dataset for a given dataset reference.
   * Used for write operations that will create new commits.
   *
   * <p>Uses materialized graphs for branch HEAD queries (instant, O(1) lookup).
   * Falls back to on-demand building for commit queries or when materialized graph unavailable.
   *
   * @param datasetRef the dataset reference
   * @return a mutable Dataset instance
   * @throws IllegalArgumentException if the reference cannot be resolved
   */
  public Dataset getMutableDataset(DatasetRef datasetRef) {
    DatasetGraph datasetGraph = getDatasetGraph(datasetRef);
    return DatasetFactory.wrap(datasetGraph);
  }

  /**
   * Gets a DatasetGraph for a given dataset reference.
   * Routes to fast path (materialized graphs) for branches or slow path (on-demand) for commits.
   *
   * @param datasetRef the dataset reference
   * @return the DatasetGraph materialized to the specified version
   * @throws IllegalArgumentException if the reference cannot be resolved
   */
  @SuppressWarnings("PMD.EmptyCatchBlock") // Exception used for control flow
  private DatasetGraph getDatasetGraph(DatasetRef datasetRef) {
    String ref = datasetRef.ref();
    String datasetName = datasetRef.datasetName();

    // Try to parse as commit ID first (historical query)
    try {
      CommitId commitId = CommitId.of(ref);
      if (commitRepository.exists(datasetName, commitId)) {
        // Commit query - use on-demand building (existing behavior)
        return getOrCreateDatasetGraph(datasetName, commitId);
      }
    } catch (IllegalArgumentException e) {
      // Not a valid commit ID, try as branch name
    }

    // Try to resolve as branch name - use materialized graph (fast path)
    if (branchRepository.exists(datasetName, ref)) {
      return getMaterializedBranchGraph(datasetName, ref);
    }

    // Neither commit nor branch found
    throw new IllegalArgumentException(
        "Cannot resolve reference: " + ref + " in dataset: " + datasetName);
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
        List.of(),
        author,
        "Initial commit",
        0  // Initial commit has empty patch
    );

    // Create empty patch for initial commit
    RDFPatch emptyPatch = RDFPatchOps.emptyPatch();

    // Save commit and patch
    commitRepository.save(datasetName, initialCommit, emptyPatch);

    // Create main branch pointing to initial commit (PROTECTED by default)
    Branch mainBranch = new Branch(
        "main",
        initialCommit.id(),
        true,                    // main is protected
        Instant.now(),          // createdAt
        Instant.now(),          // lastUpdated
        1                       // initial commit count
    );
    branchRepository.save(datasetName, mainBranch);

    // Initialize empty dataset graph for this commit
    DatasetGraphInMemory datasetGraph = new DatasetGraphInMemory();
    cacheDatasetGraph(datasetName, initialCommit.id(), datasetGraph);

    return mainBranch;
  }

  /**
   * Gets the materialized graph for a branch HEAD using the fast path.
   *
   * <p>This method provides instant access to pre-materialized branch graphs
   * maintained by the ReadModelProjector. If the materialized graph is unavailable
   * for any reason (e.g., projector hasn't processed events yet), it falls back
   * to on-demand building for graceful degradation.
   *
   * <p>Performance: O(1) instant lookup when materialized graph available,
   * O(n) fallback to on-demand building when unavailable.
   *
   * @param datasetName the dataset name
   * @param branchName the branch name
   * @return the materialized DatasetGraph for the branch HEAD
   * @throws IllegalArgumentException if the branch doesn't exist
   */
  private DatasetGraph getMaterializedBranchGraph(String datasetName,
      String branchName) {
    // Validate branch exists and get HEAD commit
    Branch branch = branchRepository.findByDatasetAndName(datasetName, branchName)
        .orElseThrow(() -> new IllegalArgumentException(
            "Cannot resolve reference: " + branchName + " in dataset: " + datasetName));

    // Try to get materialized graph (fast path)
    if (materializedBranchRepo.exists(datasetName, branchName)) {
      logger.debug("Using materialized graph for branch {}/{}", datasetName, branchName);
      DatasetGraph graph = materializedBranchRepo.getBranchGraph(datasetName, branchName);

      // Abort any active transaction to prevent nested transaction errors
      // This can happen during test isolation issues when graphs are reused across Spring contexts
      if (graph.isInTransaction()) {
        logger.warn("Found active transaction on cached graph {}/{} - aborting for safety",
            datasetName, branchName);
        graph.abort();
        logger.debug("Aborted transaction on {}/{}", datasetName, branchName);
      }

      // Return directly - MaterializedBranchRepository returns DatasetGraph
      return graph;
    } else {
      // Fallback: Materialized graph not available, build on-demand
      // This is expected during eventual consistency (projector hasn't caught up yet)
      logger.debug("Materialized graph not available for branch {}/{}. "
              + "Falling back to on-demand building (eventual consistency).",
          datasetName, branchName);
      return getOrCreateDatasetGraph(datasetName, branch.getCommitId());
    }
  }

  /**
   * Gets or creates a DatasetGraphInMemory for a specific commit (historical queries).
   * Uses Caffeine LRU cache for frequently accessed historical commits.
   *
   * <p>Note: Branch HEAD queries now use MaterializedBranchRepository (instant, O(1) lookup).
   * This method is only for historical commit queries, which are built on-demand and cached.
   *
   * @param datasetName the dataset name
   * @param commitId the commit ID
   * @return the dataset graph materialized to the specified commit
   */
  private DatasetGraphInMemory getOrCreateDatasetGraph(String datasetName, CommitId commitId) {
    CacheKey key = new CacheKey(datasetName, commitId);

    // Check cache first
    DatasetGraphInMemory graph = datasetCache.getIfPresent(key);

    if (graph == null) {
      // Cache miss - build graph on-demand
      logger.debug("Cache miss for dataset={}, commit={}. Building on-demand.",
          datasetName, commitId);
      graph = buildDatasetGraph(datasetName, commitId);
      datasetCache.put(key, graph);
    } else {
      logger.debug("Cache hit for dataset={}, commit={}", datasetName, commitId);
    }

    return graph;
  }

  /**
   * Updates tracking of latest commits.
   * Called by ReadModelProjector when branches are updated.
   *
   * <p>Note: With materialized views, branch HEADs are maintained in
   * MaterializedBranchRepository. This method is kept for backward compatibility
   * but no longer manages cache pinning.
   *
   * @param dataset the dataset name
   * @param branchName the branch name
   * @param commitId the new latest commit ID
   */
  public void updateLatestCommit(String dataset, String branchName, CommitId commitId) {
    // Branch HEADs now in MaterializedBranchRepository, no cache update needed
    logger.debug("Branch {}/{} updated to commit {} (materialized view maintained separately)",
        dataset, branchName, commitId);
  }

  /**
   * Builds a DatasetGraphInMemory by applying patches from the commit history.
   * Uses snapshots when available to speed up materialization.
   *
   * @param datasetName the dataset name
   * @param commitId the target commit ID
   * @return the materialized dataset graph
   * @throws CommitNotFoundException if the commit is not found
   */
  private DatasetGraphInMemory buildDatasetGraph(String datasetName, CommitId commitId) {
    // Try to find nearest snapshot
    Optional<Snapshot> snapshotOpt = findNearestSnapshot(datasetName, commitId);

    DatasetGraphInMemory datasetGraph;
    CommitId startCommit;

    if (snapshotOpt.isPresent()) {
      Snapshot snapshot = snapshotOpt.get();
      logger.debug("Using snapshot at commit {} for dataset {} (target: {})",
          snapshot.commitId(), datasetName, commitId);

      // Clone snapshot graph as starting point
      datasetGraph = cloneDatasetGraph(snapshot.graph());
      startCommit = snapshot.commitId();

      // If snapshot IS the target commit, we're done
      if (snapshot.commitId().equals(commitId)) {
        return datasetGraph;
      }
    } else {
      logger.debug("No snapshot found for dataset {} at commit {}, building from scratch",
          datasetName, commitId);
      datasetGraph = new DatasetGraphInMemory();
      startCommit = null;  // Start from beginning
    }

    // Apply patches from snapshot (or beginning) to target commit
    Commit targetCommit = commitRepository.findByDatasetAndId(datasetName, commitId)
        .orElseThrow(() -> new CommitNotFoundException(
            "Commit not found: " + commitId + " in dataset: " + datasetName, true));
    applyPatchHistorySince(datasetName, targetCommit, datasetGraph, startCommit);

    return datasetGraph;
  }

  /**
   * Finds the nearest snapshot at or before the target commit.
   * Queries Kafka to find the best snapshot that is an ancestor of the target commit.
   *
   * @param datasetName the dataset name
   * @param targetCommit the target commit ID
   * @return Optional containing the nearest snapshot, or empty if none found
   */
  private Optional<Snapshot> findNearestSnapshot(String datasetName, CommitId targetCommit) {
    // Query SnapshotService to find best snapshot from Kafka
    Optional<SnapshotKafkaStore.SnapshotInfo> snapshotInfo =
        snapshotService.findBestSnapshot(datasetName, targetCommit);

    if (snapshotInfo.isEmpty()) {
      return Optional.empty();
    }

    // Fetch the actual snapshot data from Kafka (only if we need it)
    return Optional.of(snapshotService.fetchSnapshot(snapshotInfo.get()));
  }

  /**
   * Applies patches from a starting commit to target commit.
   * If startCommit is null, applies from the beginning.
   *
   * @param datasetName the dataset name
   * @param targetCommit the target commit
   * @param datasetGraph the dataset graph to apply patches to
   * @param startCommit the starting commit (exclusive), or null to start from beginning
   */
  private void applyPatchHistorySince(String datasetName, Commit targetCommit,
      DatasetGraphInMemory datasetGraph, CommitId startCommit) {

    // Get commit chain from startCommit to targetCommit
    List<Commit> commits = getCommitChain(datasetName, targetCommit, startCommit);

    // Apply patches in order
    for (Commit commit : commits) {
      commitRepository.findPatchByDatasetAndId(datasetName, commit.id())
          .ifPresent(patch -> applyPatchWithoutTransactionManagement(datasetGraph, patch));
    }
  }

  /**
   * Gets the chain of commits from startCommit (exclusive) to targetCommit (inclusive).
   * Returns commits in chronological order (oldest first).
   *
   * @param datasetName the dataset name
   * @param targetCommit the target commit
   * @param startCommit the starting commit (exclusive), or null to include all ancestors
   * @return List of commits in chronological order
   */
  private List<Commit> getCommitChain(String datasetName, Commit targetCommit,
      CommitId startCommit) {
    List<Commit> chain = new ArrayList<>();
    collectCommitChain(datasetName, targetCommit, startCommit, chain);
    Collections.reverse(chain);  // Oldest first
    return chain;
  }

  /**
   * Recursively collects the commit chain into a list.
   * Stops when reaching the startCommit (which is not included).
   *
   * @param datasetName the dataset name
   * @param current the current commit
   * @param stopAt the commit ID to stop at (exclusive), or null to collect all
   * @param chain the list to collect commits into
   */
  private void collectCommitChain(String datasetName, Commit current, CommitId stopAt,
      List<Commit> chain) {
    if (stopAt != null && stopAt.equals(current.id())) {
      return;  // Reached snapshot commit (don't include it)
    }

    chain.add(current);

    // Recursively process parents
    for (CommitId parentId : current.parents()) {
      Commit parent = commitRepository.findByDatasetAndId(datasetName, parentId).orElse(null);
      if (parent != null) {
        collectCommitChain(datasetName, parent, stopAt, chain);
      }
    }
  }

  /**
   * Clones a DatasetGraph (deep copy).
   * Creates a new in-memory dataset graph with all triples from the source.
   *
   * @param source the source dataset graph to clone
   * @return a new DatasetGraphInMemory with all data copied from source
   */
  private DatasetGraphInMemory cloneDatasetGraph(DatasetGraph source) {
    DatasetGraphInMemory clone = new DatasetGraphInMemory();

    // Copy default graph
    source.getDefaultGraph().find().forEachRemaining(triple -> {
      clone.getDefaultGraph().add(triple);
    });

    // Copy named graphs
    source.listGraphNodes().forEachRemaining(graphNode -> {
      source.getGraph(graphNode).find().forEachRemaining(triple -> {
        clone.getGraph(graphNode).add(triple);
      });
    });

    return clone;
  }

  /**
   * Caches a dataset graph for a specific commit.
   * This method can be used to store pre-materialized graphs (e.g., from snapshots)
   * to avoid rebuilding them from patches.
   *
   * @param datasetName the dataset name
   * @param commitId the commit ID
   * @param datasetGraph the dataset graph to cache
   */
  public void cacheDatasetGraph(String datasetName, CommitId commitId,
                                  DatasetGraph datasetGraph) {
    // Convert to DatasetGraphInMemory if needed
    DatasetGraphInMemory memGraph;
    if (datasetGraph instanceof DatasetGraphInMemory) {
      memGraph = (DatasetGraphInMemory) datasetGraph;
    } else {
      // Copy to in-memory graph
      memGraph = new DatasetGraphInMemory();
      datasetGraph.find().forEachRemaining(memGraph::add);
    }

    CacheKey key = new CacheKey(datasetName, commitId);
    datasetCache.put(key, memGraph);
  }

  /**
   * Materializes a dataset graph at a specific commit.
   * This creates a snapshot of the dataset state at the given commit by applying
   * all patches in the commit history.
   *
   * @param datasetName the dataset name
   * @param commitId the commit ID to materialize
   * @return the dataset graph at the specified commit
   * @throws CommitNotFoundException if the commit is not found
   */
  public DatasetGraph materializeCommit(String datasetName, CommitId commitId) {
    return getOrCreateDatasetGraph(datasetName, commitId);
  }

  /**
   * Materializes a dataset at a specific commit.
   * Convenience method that returns a Dataset wrapped around the materialized DatasetGraph.
   *
   * @param datasetName the dataset name
   * @param commitId the commit ID to materialize
   * @return the dataset at the specified commit
   * @throws CommitNotFoundException if the commit is not found
   */
  @Timed(
      value = "dataset.materialize",
      description = "Dataset materialization time"
  )
  public Dataset materializeAtCommit(String datasetName, CommitId commitId) {
    DatasetGraph datasetGraph = materializeCommit(datasetName, commitId);
    return DatasetFactory.wrap(datasetGraph);
  }

  /**
   * Gets a named graph from a dataset at a specific commit.
   *
   * @param datasetName the dataset name
   * @param commitId the commit ID
   * @param graphIri the graph IRI
   * @return the named graph as a Model, or null if the graph doesn't exist
   * @throws CommitNotFoundException if the commit is not found
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
   * @throws CommitNotFoundException if the commit is not found
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
      Optional<RDFPatch> patchOpt =
          commitRepository.findPatchByDatasetAndId(datasetName, currentCommitId);

      if (patchOpt.isPresent()) {
        RDFPatch patch = patchOpt.get();

        // Check if this patch modifies the target graph
        if (patchModifiesGraph(patch, graphIri)) {
          return currentCommitId;
        }
      }

      // Move to parent commit
      Optional<Commit> commitOpt =
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

      @SuppressWarnings("null")
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
   * <p>Note: This only clears the historical commit cache.
   * Branch HEADs are maintained in MaterializedBranchRepository.
   *
   * @param datasetName the dataset name
   */
  public void clearCache(String datasetName) {
    // Remove all entries for this dataset
    datasetCache.asMap().keySet().removeIf(key -> key.dataset().equals(datasetName));

    logger.info("Cleared historical commit cache for dataset: {}", datasetName);
  }

  /**
   * Clears all dataset caches.
   *
   * <p>Note: This only clears the historical commit cache.
   * Branch HEADs are maintained in MaterializedBranchRepository.
   */
  public void clearAllCaches() {
    datasetCache.invalidateAll();
    logger.info("Cleared all historical commit caches");
  }

  /**
   * Apply an RDF patch directly to a dataset graph without transaction management.
   *
   * <p>This method creates a custom RDFChangesApply that skips transaction operations
   * to prevent nested transaction errors. This is necessary when applying patches during
   * graph materialization where transactions are not needed for in-memory graphs.
   *
   * <p>Pattern: Same approach as {@link InMemoryMaterializedBranchRepository#applyPatchDirect}
   * which uses locks instead of transactions for concurrency control.
   *
   * @param datasetGraph the target dataset graph
   * @param patch the patch to apply
   * @see InMemoryMaterializedBranchRepository#applyPatchDirect
   */
  private void applyPatchWithoutTransactionManagement(DatasetGraph datasetGraph, RDFPatch patch) {
    // Create a changes applier that skips transaction management
    RDFChangesApply changes = new RDFChangesApply(datasetGraph) {
      @Override
      public void txnBegin() {
        // Skip transaction begin - caller manages transactions
      }

      @Override
      public void txnCommit() {
        // Skip transaction commit - caller manages transactions
      }

      @Override
      public void txnAbort() {
        // Skip transaction abort - caller manages transactions
      }
    };

    // Apply the patch
    patch.apply(changes);
  }
}
