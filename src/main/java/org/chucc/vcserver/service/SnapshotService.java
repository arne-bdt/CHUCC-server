package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.jena.query.Dataset;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.DatasetRef;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.SnapshotCreatedEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for managing periodic snapshots of datasets.
 * Creates snapshots asynchronously every N commits to speed up recovery.
 * Snapshots are serialized as N-Quads and published to Kafka.
 */
@Service
public class SnapshotService {
  private static final Logger logger = LoggerFactory.getLogger(SnapshotService.class);

  private final DatasetService datasetService;
  private final BranchRepository branchRepository;
  private final EventPublisher eventPublisher;
  private final VersionControlProperties vcProperties;

  // Track commit counts per (dataset, branch) for snapshot triggering
  private final Map<String, Map<String, AtomicLong>> commitCounters = new ConcurrentHashMap<>();

  /**
   * Constructs a SnapshotService.
   *
   * @param datasetService the dataset service
   * @param branchRepository the branch repository
   * @param eventPublisher the event publisher
   * @param vcProperties the version control properties
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "All dependencies are Spring-managed beans and are thread-safe")
  public SnapshotService(
      DatasetService datasetService,
      BranchRepository branchRepository,
      EventPublisher eventPublisher,
      VersionControlProperties vcProperties) {
    this.datasetService = datasetService;
    this.branchRepository = branchRepository;
    this.eventPublisher = eventPublisher;
    this.vcProperties = vcProperties;
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
}
