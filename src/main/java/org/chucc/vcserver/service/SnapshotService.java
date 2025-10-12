package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.jena.query.Dataset;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.mem.DatasetGraphInMemory;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.DatasetRef;
import org.chucc.vcserver.domain.Snapshot;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.SnapshotCreatedEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for managing periodic snapshots of datasets.
 * Creates snapshots asynchronously every N commits to speed up recovery.
 * Snapshots are serialized as N-Quads and published to Kafka.
 * Snapshots are queried from Kafka on-demand (not stored in memory).
 */
@Service
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class SnapshotService {
  private static final Logger logger = LoggerFactory.getLogger(SnapshotService.class);

  private final DatasetService datasetService;
  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;
  private final EventPublisher eventPublisher;
  private final VersionControlProperties vcProperties;
  private final SnapshotKafkaStore kafkaStore;

  // Track commit counts per (dataset, branch) for snapshot triggering
  private final Map<String, Map<String, AtomicLong>> commitCounters = new ConcurrentHashMap<>();

  /**
   * Constructs a SnapshotService.
   *
   * @param datasetService the dataset service
   * @param branchRepository the branch repository
   * @param commitRepository the commit repository
   * @param eventPublisher the event publisher
   * @param vcProperties the version control properties
   * @param kafkaStore the kafka store for snapshot queries
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "All dependencies are Spring-managed beans and are thread-safe")
  public SnapshotService(
      DatasetService datasetService,
      BranchRepository branchRepository,
      CommitRepository commitRepository,
      EventPublisher eventPublisher,
      VersionControlProperties vcProperties,
      SnapshotKafkaStore kafkaStore) {
    this.datasetService = datasetService;
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
    this.eventPublisher = eventPublisher;
    this.vcProperties = vcProperties;
    this.kafkaStore = kafkaStore;
  }

  /**
   * Records a commit and triggers snapshot creation if the interval is reached.
   * This method should be called after each commit is successfully created.
   *
   * @param datasetName the dataset name
   * @param branchName the branch name
   * @param commitId the commit ID
   */
  public void recordCommit(String datasetName, String branchName, CommitId commitId) {
    if (!vcProperties.isSnapshotsEnabled()) {
      return;
    }

    long count = commitCounters
        .computeIfAbsent(datasetName, k -> new ConcurrentHashMap<>())
        .computeIfAbsent(branchName, k -> new AtomicLong(0))
        .incrementAndGet();

    logger.debug("Recorded commit {} for {}/{}, count: {}",
        commitId, datasetName, branchName, count);

    if (count % vcProperties.getSnapshotInterval() == 0) {
      logger.info("Snapshot interval reached for {}/{} at commit {} (count: {})",
          datasetName, branchName, commitId, count);
      createSnapshotAsync(datasetName, branchName, commitId);
    }
  }

  /**
   * Creates a snapshot asynchronously in a separate thread.
   * The snapshot contains the complete state of the dataset at the given commit,
   * serialized as N-Quads.
   *
   * @param datasetName the dataset name
   * @param branchName the branch name
   * @param commitId the commit ID
   */
  @Async("snapshotExecutor")
  public void createSnapshotAsync(String datasetName, String branchName, CommitId commitId) {
    try {
      logger.info("Starting snapshot creation for {}/{} at commit {}",
          datasetName, branchName, commitId);

      // Get the dataset at the specified commit
      DatasetRef ref = new DatasetRef(datasetName, commitId.value());
      Dataset dataset = datasetService.getDataset(ref);

      // Serialize to N-Quads
      String nquads = serializeToNquads(dataset.asDatasetGraph());

      // Create and publish snapshot event
      SnapshotCreatedEvent event = new SnapshotCreatedEvent(
          datasetName,
          commitId.value(),
          branchName,
          Instant.now(),
          nquads
      );

      eventPublisher.publish(event);

      logger.info("Successfully created snapshot for {}/{} at commit {} ({} bytes)",
          datasetName, branchName, commitId, nquads.length());

    } catch (Exception e) {
      logger.error("Failed to create snapshot for {}/{} at commit {}",
          datasetName, branchName, commitId, e);
      // Don't rethrow - snapshot failures shouldn't break commit operations
    }
  }

  /**
   * Serializes a DatasetGraph to N-Quads format.
   *
   * @param datasetGraph the dataset graph to serialize
   * @return the N-Quads string representation
   */
  private String serializeToNquads(DatasetGraph datasetGraph) {
    StringWriter writer = new StringWriter();
    RDFDataMgr.write(writer, datasetGraph, Lang.NQUADS);
    return writer.toString();
  }

  /**
   * Resets the commit counter for a branch.
   * Useful when a branch is reset or deleted.
   *
   * @param datasetName the dataset name
   * @param branchName the branch name
   */
  public void resetCounter(String datasetName, String branchName) {
    Map<String, AtomicLong> branchCounters = commitCounters.get(datasetName);
    if (branchCounters != null) {
      branchCounters.remove(branchName);
      logger.debug("Reset commit counter for {}/{}", datasetName, branchName);
    }
  }

  /**
   * Gets the current commit count for a branch.
   *
   * @param datasetName the dataset name
   * @param branchName the branch name
   * @return the commit count
   */
  public long getCommitCount(String datasetName, String branchName) {
    return commitCounters
        .getOrDefault(datasetName, Map.of())
        .getOrDefault(branchName, new AtomicLong(0))
        .get();
  }

  /**
   * Clears all commit counters.
   * Useful for testing or when reconfiguring snapshot intervals.
   */
  public void clearAllCounters() {
    commitCounters.clear();
    logger.debug("Cleared all commit counters");
  }

  /**
   * Clears commit counters for a specific dataset.
   * Useful when deleting a dataset.
   *
   * @param datasetName the dataset name
   */
  public void clearSnapshotsForDataset(String datasetName) {
    commitCounters.remove(datasetName);
    logger.debug("Cleared snapshot counters for dataset: {}", datasetName);
  }

  /**
   * Finds the most recent snapshot for a dataset that is an ancestor of the target commit.
   * Queries Kafka snapshot topic to find snapshots on-demand (not stored in memory).
   *
   * @param datasetName the dataset name
   * @param targetCommit the commit we're trying to materialize
   * @return Optional containing the best snapshot to use, or empty if none found
   */
  public Optional<SnapshotKafkaStore.SnapshotInfo> findBestSnapshot(
      String datasetName, CommitId targetCommit) {
    logger.debug("Finding best snapshot for dataset {} at target commit {}",
        datasetName, targetCommit);

    // Skip if snapshots are disabled
    if (!vcProperties.isSnapshotsEnabled()) {
      logger.debug("Snapshots disabled, skipping snapshot lookup");
      return Optional.empty();
    }

    // Load snapshot metadata from Kafka
    List<SnapshotKafkaStore.SnapshotInfo> metadata = kafkaStore.findSnapshotMetadata(datasetName);

    if (metadata.isEmpty()) {
      logger.debug("No snapshots found in Kafka for dataset {}", datasetName);
      return Optional.empty();
    }

    // Find the best snapshot that's an ancestor of targetCommit
    Optional<SnapshotKafkaStore.SnapshotInfo> best = metadata.stream()
        .filter(info -> isAncestor(datasetName, targetCommit, info.commitId()))
        .max(Comparator.comparing(SnapshotKafkaStore.SnapshotInfo::timestamp));

    if (best.isPresent()) {
      logger.debug("Found best snapshot at commit {} for target {}",
          best.get().commitId(), targetCommit);
    } else {
      logger.debug("No ancestor snapshots found for target commit {}", targetCommit);
    }

    return best;
  }

  /**
   * Fetches the actual snapshot data from Kafka.
   *
   * @param info the snapshot metadata
   * @return the snapshot with materialized graph
   */
  public Snapshot fetchSnapshot(SnapshotKafkaStore.SnapshotInfo info) {
    logger.debug("Fetching snapshot from Kafka: {} at offset {}",
        info.topic(), info.offset());

    // Fetch the event from Kafka at specific topic/partition/offset
    SnapshotCreatedEvent event = kafkaStore.fetchSnapshotEvent(info);

    // Deserialize N-Quads into DatasetGraph
    DatasetGraph graph = deserializeNquads(event.nquads());

    logger.debug("Fetched and deserialized snapshot at commit {}", info.commitId());

    return new Snapshot(
        info.commitId(),
        info.branchName(),
        info.timestamp(),
        graph
    );
  }

  /**
   * Deserializes N-Quads string into a DatasetGraph.
   *
   * @param nquads the N-Quads string
   * @return the parsed DatasetGraph
   */
  private DatasetGraph deserializeNquads(String nquads) {
    DatasetGraphInMemory datasetGraph = new DatasetGraphInMemory();
    try (StringReader reader = new StringReader(nquads)) {
      RDFDataMgr.read(datasetGraph, reader, null, Lang.NQUADS);
    }
    return datasetGraph;
  }

  /**
   * Checks if a commit is an ancestor of another commit.
   *
   * @param datasetName the dataset name
   * @param descendant the potential descendant commit
   * @param ancestorCandidate the potential ancestor commit
   * @return true if ancestorCandidate is an ancestor of descendant
   */
  private boolean isAncestor(String datasetName, CommitId descendant, CommitId ancestorCandidate) {
    if (descendant.equals(ancestorCandidate)) {
      return true;  // Same commit
    }

    Set<CommitId> visited = new LinkedHashSet<>();
    return checkAncestor(datasetName, descendant, ancestorCandidate, visited);
  }

  /**
   * Recursively checks if a commit is an ancestor.
   *
   * @param datasetName the dataset name
   * @param current the current commit being checked
   * @param target the target ancestor we're looking for
   * @param visited set of visited commits to prevent cycles
   * @return true if target is an ancestor of current
   */
  private boolean checkAncestor(String datasetName, CommitId current, CommitId target,
      Set<CommitId> visited) {
    if (current.equals(target)) {
      return true;
    }

    if (visited.contains(current)) {
      return false;  // Already checked this path
    }

    visited.add(current);

    Commit commit = commitRepository.findByDatasetAndId(datasetName, current).orElse(null);
    if (commit == null) {
      return false;
    }

    // Check all parent paths
    for (CommitId parent : commit.parents()) {
      if (checkAncestor(datasetName, parent, target, visited)) {
        return true;
      }
    }

    return false;
  }
}
