package org.chucc.vcserver.projection;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.mem.DatasetGraphInMemory;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.BatchGraphsCompletedEvent;
import org.chucc.vcserver.event.BranchCreatedEvent;
import org.chucc.vcserver.event.BranchDeletedEvent;
import org.chucc.vcserver.event.BranchRebasedEvent;
import org.chucc.vcserver.event.BranchResetEvent;
import org.chucc.vcserver.event.CherryPickedEvent;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.CommitsSquashedEvent;
import org.chucc.vcserver.event.DatasetDeletedEvent;
import org.chucc.vcserver.event.RevertCreatedEvent;
import org.chucc.vcserver.event.SnapshotCreatedEvent;
import org.chucc.vcserver.event.TagCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.SnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Read-model projector that rebuilds in-memory graphs by consuming
 * version control events from Kafka and applying RDF Patches in order.
 *
 * <p>On startup, consumes from earliest offset to build state.
 * Maintains (dataset, branch) → commitId mappings and materialized graphs.
 * Applies patches to the branch's dataset in order.
 */
@Service
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class ReadModelProjector {
  private static final Logger logger = LoggerFactory.getLogger(ReadModelProjector.class);

  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;
  private final DatasetService datasetService;
  private final SnapshotService snapshotService;

  // Track snapshot checkpoints per (dataset, branch) to skip earlier events during recovery
  private final Map<String, Map<String, CommitId>> snapshotCheckpoints = new ConcurrentHashMap<>();

  /**
   * Constructs a ReadModelProjector.
   *
   * @param branchRepository the branch repository
   * @param commitRepository the commit repository
   * @param datasetService the dataset service
   * @param snapshotService the snapshot service
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared")
  public ReadModelProjector(
      BranchRepository branchRepository,
      CommitRepository commitRepository,
      DatasetService datasetService,
      SnapshotService snapshotService) {
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
    this.datasetService = datasetService;
    this.snapshotService = snapshotService;
  }

  /**
   * Kafka listener for version control events.
   * Consumes events from all dataset topics matching the pattern.
   *
   * @param event the version control event
   */
  @KafkaListener(
      topicPattern = "vc\\..*\\.events",
      groupId = "${spring.kafka.consumer.group-id:read-model-projector}",
      containerFactory = "kafkaListenerContainerFactory",
      autoStartup = "${projector.kafka-listener.enabled:true}"
  )
  @Timed(
      value = "event.projector.processing",
      description = "Event processing time"
  )
  @Counted(
      value = "event.projector.processed",
      description = "Events processed count"
  )
  public void handleEvent(VersionControlEvent event) {
    logger.debug("Received event: {} for dataset: {}",
        event.getClass().getSimpleName(), event.dataset());

    try {
      switch (event) {
        case CommitCreatedEvent e -> handleCommitCreated(e);
        case BranchCreatedEvent e -> handleBranchCreated(e);
        case BranchResetEvent e -> handleBranchReset(e);
        case BranchRebasedEvent e -> handleBranchRebased(e);
        case BranchDeletedEvent e -> handleBranchDeleted(e);
        case DatasetDeletedEvent e -> handleDatasetDeleted(e);
        case TagCreatedEvent e -> handleTagCreated(e);
        case RevertCreatedEvent e -> handleRevertCreated(e);
        case SnapshotCreatedEvent e -> handleSnapshotCreated(e);
        case CherryPickedEvent e -> handleCherryPicked(e);
        case CommitsSquashedEvent e -> handleCommitsSquashed(e);
        case BatchGraphsCompletedEvent e -> handleBatchGraphsCompleted(e);
      }

      logger.info("Successfully projected event: {} for dataset: {}",
          event.getClass().getSimpleName(), event.dataset());
    } catch (Exception ex) {
      logger.error("Failed to project event: {} for dataset: {}",
          event.getClass().getSimpleName(), event.dataset(), ex);
      // Re-throw to trigger retry/DLQ handling if configured
      throw new ProjectionException("Failed to project event", ex);
    }
  }

  /**
   * Handles CommitCreatedEvent by saving the commit and its RDF patch.
   * If the event includes a branch name, updates that branch to point to the new commit.
   *
   * @param event the commit created event
   */
  void handleCommitCreated(CommitCreatedEvent event) {
    logger.debug("Processing CommitCreatedEvent: commitId={}, branch={}, dataset={}",
        event.commitId(), event.branch(), event.dataset());

    // Parse RDF Patch from string
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        event.rdfPatch().getBytes(StandardCharsets.UTF_8));
    RDFPatch patch = RDFPatchOps.read(inputStream);

    // Create Commit domain object
    Commit commit = new Commit(
        CommitId.of(event.commitId()),
        event.parents().stream()
            .map(CommitId::of)
            .toList(),
        event.author(),
        event.message(),
        event.timestamp()
    );

    // Save commit and patch
    commitRepository.save(event.dataset(), commit, patch);

    logger.debug("Saved commit: {} with {} parent(s) in dataset: {}",
        commit.id(), commit.parents().size(), event.dataset());

    // Update branch HEAD if branch is specified (nullable for backward compatibility)
    if (event.branch() != null) {
      // Check if branch exists before updating (for test isolation with shared Kafka topics)
      Optional<Branch> existingBranch = branchRepository.findByDatasetAndName(
          event.dataset(), event.branch());

      if (existingBranch.isPresent()) {
        branchRepository.updateBranchHead(
            event.dataset(),
            event.branch(),
            CommitId.of(event.commitId())
        );

        logger.debug("Updated branch: {} to point to commit: {} in dataset: {}",
            event.branch(), event.commitId(), event.dataset());
      } else {
        logger.debug("Skipping branch update for non-existent branch: {} in dataset: {} "
                + "(event from different test/dataset)",
            event.branch(), event.dataset());
      }
    }
  }

  /**
   * Handles BranchCreatedEvent by creating a new branch.
   *
   * @param event the branch created event
   */
  void handleBranchCreated(BranchCreatedEvent event) {
    logger.debug("Processing BranchCreatedEvent: branchName={}, commitId={}, dataset={}",
        event.branchName(), event.commitId(), event.dataset());

    Branch branch = new Branch(
        event.branchName(),
        CommitId.of(event.commitId())
    );

    branchRepository.save(event.dataset(), branch);

    logger.debug("Created branch: {} pointing to {} in dataset: {}",
        event.branchName(), event.commitId(), event.dataset());
  }

  /**
   * Handles BranchResetEvent by updating the branch head.
   *
   * @param event the branch reset event
   */
  void handleBranchReset(BranchResetEvent event) {
    logger.debug("Processing BranchResetEvent: branchName={}, from={}, to={}, dataset={}",
        event.branchName(), event.fromCommitId(), event.toCommitId(), event.dataset());

    // Check if branch exists before resetting
    if (branchRepository.findByDatasetAndName(event.dataset(), event.branchName()).isEmpty()) {
      logger.debug("Skipping reset for non-existent branch: {} in dataset: {} "
              + "(event from different test/dataset)",
          event.branchName(), event.dataset());
      return;
    }

    branchRepository.updateBranchHead(
        event.dataset(),
        event.branchName(),
        CommitId.of(event.toCommitId())
    );

    logger.debug("Reset branch: {} from {} to {} in dataset: {}",
        event.branchName(), event.fromCommitId(), event.toCommitId(), event.dataset());

    // Trigger snapshot check after branch update
    snapshotService.recordCommit(event.dataset(), event.branchName(),
        CommitId.of(event.toCommitId()));
  }

  /**
   * Handles BranchDeletedEvent by removing the branch from the repository.
   *
   * @param event the branch deleted event
   */
  void handleBranchDeleted(BranchDeletedEvent event) {
    logger.debug("Processing BranchDeletedEvent: branchName={}, lastCommitId={}, dataset={}",
        event.branchName(), event.lastCommitId(), event.dataset());

    boolean deleted = branchRepository.delete(event.dataset(), event.branchName());

    if (deleted) {
      logger.info("Deleted branch: {} from dataset: {} (was at commit: {})",
          event.branchName(), event.dataset(), event.lastCommitId());
    } else {
      logger.warn("Branch {} not found in dataset {} during deletion "
              + "(event from different test/dataset)",
          event.branchName(), event.dataset());
    }
  }

  /**
   * Handles DatasetDeletedEvent by clearing all in-memory data for the dataset.
   *
   * @param event the dataset deleted event
   */
  void handleDatasetDeleted(DatasetDeletedEvent event) {
    logger.warn("Processing DatasetDeletedEvent: dataset={}, branches={}, commits={}",
        event.dataset(), event.deletedBranches().size(), event.deletedCommitCount());

    // 1. Delete all branches
    branchRepository.deleteAllByDataset(event.dataset());

    // 2. Delete all commits
    commitRepository.deleteAllByDataset(event.dataset());

    // 3. Clear dataset cache
    datasetService.clearCache(event.dataset());

    // 4. Clear snapshot cache
    snapshotService.clearSnapshotsForDataset(event.dataset());

    logger.warn("Dataset {} fully deleted from memory (Kafka topic deleted: {})",
        event.dataset(), event.kafkaTopicDeleted());
  }

  /**
   * Handles BranchRebasedEvent by updating the branch head.
   * Note: The individual rebased commits are created via separate CommitCreatedEvents.
   *
   * @param event the branch rebased event
   */
  void handleBranchRebased(BranchRebasedEvent event) {
    logger.debug("Processing BranchRebasedEvent: branch={}, previousHead={}, newHead={}, "
            + "rebasedCount={}, dataset={}",
        event.branch(), event.previousHead(), event.newHead(),
        event.newCommits().size(), event.dataset());

    // Check if branch exists before rebasing
    if (branchRepository.findByDatasetAndName(event.dataset(), event.branch()).isEmpty()) {
      logger.debug("Skipping rebase for non-existent branch: {} in dataset: {} "
              + "(event from different test/dataset)",
          event.branch(), event.dataset());
      return;
    }

    // Update branch to point to final rebased commit
    branchRepository.updateBranchHead(
        event.dataset(),
        event.branch(),
        CommitId.of(event.newHead())
    );

    logger.debug("Rebased branch: {} from {} to {} ({} commits) in dataset: {}",
        event.branch(), event.previousHead(), event.newHead(),
        event.newCommits().size(), event.dataset());

    // Trigger snapshot check after branch update
    snapshotService.recordCommit(event.dataset(), event.branch(),
        CommitId.of(event.newHead()));
  }

  /**
   * Handles TagCreatedEvent (currently logs only, can be extended for tag repository).
   *
   * @param event the tag created event
   */
  void handleTagCreated(TagCreatedEvent event) {
    logger.debug("Processing TagCreatedEvent: tagName={}, commitId={}, dataset={}",
        event.tagName(), event.commitId(), event.dataset());

    // Tag handling can be implemented when TagRepository is available
    logger.info("Tag created: {} -> {} in dataset: {}",
        event.tagName(), event.commitId(), event.dataset());
  }

  /**
   * Handles RevertCreatedEvent by saving the revert commit.
   *
   * @param event the revert created event
   */
  void handleRevertCreated(RevertCreatedEvent event) {
    logger.debug("Processing RevertCreatedEvent: revertCommitId={}, revertedCommitId={}, "
            + "branch={}, dataset={}",
        event.revertCommitId(), event.revertedCommitId(), event.branch(), event.dataset());

    // Get the target branch to determine the parent commit (current HEAD)
    Optional<Branch> targetBranchOpt = branchRepository.findByDatasetAndName(
        event.dataset(),
        event.branch());

    if (targetBranchOpt.isEmpty()) {
      logger.debug("Skipping revert for non-existent branch: {} in dataset: {} "
              + "(event from different test/dataset)",
          event.branch(), event.dataset());
      return;
    }

    Branch targetBranch = targetBranchOpt.get();

    // Parse RDF Patch from string
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        event.rdfPatch().getBytes(StandardCharsets.UTF_8));
    RDFPatch patch = RDFPatchOps.read(inputStream);

    // Create Commit domain object for revert
    // The revert commit's parent is the current HEAD of the branch
    Commit commit = new Commit(
        CommitId.of(event.revertCommitId()),
        java.util.List.of(targetBranch.getCommitId()),
        event.author(),
        event.message(),
        event.timestamp()
    );

    // Save revert commit and patch
    commitRepository.save(event.dataset(), commit, patch);

    // Update the target branch to point to the new revert commit
    branchRepository.updateBranchHead(
        event.dataset(),
        event.branch(),
        CommitId.of(event.revertCommitId())
    );

    logger.debug("Saved revert commit: {} reverting {} on branch {} in dataset: {}",
        commit.id(), event.revertedCommitId(), event.branch(), event.dataset());
  }

  /**
   * Handles SnapshotCreatedEvent by caching the materialized graph.
   * Snapshots are stored in Kafka and queried on-demand (not kept in memory).
   * However, we cache the materialized graph in DatasetService for performance.
   *
   * @param event the snapshot created event
   */
  void handleSnapshotCreated(SnapshotCreatedEvent event) {
    logger.debug("Processing SnapshotCreatedEvent: branchName={}, commitId={}, dataset={}",
        event.branchName(), event.commitId(), event.dataset());

    try {
      // Parse N-Quads into DatasetGraph
      DatasetGraph graph = parseNquads(event.nquads());

      // Cache the materialized graph in DatasetService
      // This allows the system to use the snapshot as a base for building later commits
      // Note: Snapshot metadata is stored in Kafka and queried on-demand
      datasetService.cacheDatasetGraph(
          event.dataset(),
          CommitId.of(event.commitId()),
          graph
      );

      // Set checkpoint to skip earlier events during recovery
      snapshotCheckpoints
          .computeIfAbsent(event.dataset(), k -> new ConcurrentHashMap<>())
          .put(event.branchName(), CommitId.of(event.commitId()));

      // Count quads for logging
      long quadCount = 0;
      java.util.Iterator<org.apache.jena.sparql.core.Quad> quadIter = graph.find();
      while (quadIter.hasNext()) {
        quadIter.next();
        quadCount++;
      }

      logger.info("Cached snapshot graph for {}/{} at commit {} ({} quads)",
          event.dataset(), event.branchName(), event.commitId(), quadCount);
    } catch (Exception e) {
      logger.error("Failed to cache snapshot for {}/{} at commit {}",
          event.dataset(), event.branchName(), event.commitId(), e);
      // Don't rethrow - snapshot failures shouldn't break event processing
      // System will fall back to querying Kafka on-demand
    }
  }

  /**
   * Parses N-Quads string into a DatasetGraph.
   *
   * @param nquads the N-Quads string
   * @return the parsed DatasetGraph
   */
  private DatasetGraph parseNquads(String nquads) {
    DatasetGraphInMemory datasetGraph = new DatasetGraphInMemory();
    try (StringReader reader = new StringReader(nquads)) {
      RDFDataMgr.read(datasetGraph, reader, null, Lang.NQUADS);
    }
    return datasetGraph;
  }

  /**
   * Handles CherryPickedEvent by saving the cherry-picked commit.
   *
   * @param event the cherry-picked event
   */
  void handleCherryPicked(CherryPickedEvent event) {
    logger.debug("Processing CherryPickedEvent: newCommitId={}, sourceCommitId={}, "
            + "branch={}, dataset={}",
        event.newCommitId(), event.sourceCommitId(), event.branch(), event.dataset());

    // Get the target branch to determine the parent commit
    Optional<Branch> targetBranchOpt = branchRepository.findByDatasetAndName(
        event.dataset(),
        event.branch());

    if (targetBranchOpt.isEmpty()) {
      logger.debug("Skipping cherry-pick for non-existent branch: {} in dataset: {} "
              + "(event from different test/dataset)",
          event.branch(), event.dataset());
      return;
    }

    Branch targetBranch = targetBranchOpt.get();

    // Parse RDF Patch from string
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        event.rdfPatch().getBytes(StandardCharsets.UTF_8));
    RDFPatch patch = RDFPatchOps.read(inputStream);

    // Create Commit domain object for cherry-picked commit
    // The parent is the current HEAD of the target branch
    Commit commit = new Commit(
        CommitId.of(event.newCommitId()),
        java.util.List.of(targetBranch.getCommitId()),
        event.author(),
        event.message(),
        event.timestamp()
    );

    // Save cherry-picked commit and patch
    commitRepository.save(event.dataset(), commit, patch);

    // Update the target branch to point to the new commit
    branchRepository.updateBranchHead(
        event.dataset(),
        event.branch(),
        CommitId.of(event.newCommitId())
    );

    logger.debug("Saved cherry-picked commit: {} from source {} on branch {} in dataset: {}",
        commit.id(), event.sourceCommitId(), event.branch(), event.dataset());

    // Trigger snapshot check after branch update
    snapshotService.recordCommit(event.dataset(), event.branch(),
        CommitId.of(event.newCommitId()));
  }

  /**
   * Handles CommitsSquashedEvent by updating the branch head.
   * Note: The squashed commit is created by the command handler.
   *
   * @param event the commits squashed event
   */
  void handleCommitsSquashed(CommitsSquashedEvent event) {
    logger.debug("Processing CommitsSquashedEvent: branch={}, previousHead={}, newCommitId={}, "
            + "squashedCount={}, dataset={}",
        event.branch(), event.previousHead(), event.newCommitId(),
        event.squashedCommitIds().size(), event.dataset());

    // Check if branch exists before squashing
    if (branchRepository.findByDatasetAndName(event.dataset(), event.branch()).isEmpty()) {
      logger.debug("Skipping squash for non-existent branch: {} in dataset: {} "
              + "(event from different test/dataset)",
          event.branch(), event.dataset());
      return;
    }

    // Update branch to point to squashed commit
    branchRepository.updateBranchHead(
        event.dataset(),
        event.branch(),
        CommitId.of(event.newCommitId())
    );

    logger.debug("Squashed {} commits on branch: {} from {} to {} in dataset: {}",
        event.squashedCommitIds().size(), event.branch(), event.previousHead(),
        event.newCommitId(), event.dataset());

    // Trigger snapshot check after branch update
    snapshotService.recordCommit(event.dataset(), event.branch(),
        CommitId.of(event.newCommitId()));
  }

  /**
   * Handles BatchGraphsCompletedEvent by processing each commit in the batch.
   *
   * @param event the batch graphs completed event
   */
  void handleBatchGraphsCompleted(BatchGraphsCompletedEvent event) {
    logger.debug("Processing BatchGraphsCompletedEvent: dataset={}, commitCount={}",
        event.dataset(), event.commits().size());

    // Process each commit in the batch
    for (CommitCreatedEvent commitEvent : event.commits()) {
      handleCommitCreated(commitEvent);
    }

    logger.info("Processed batch with {} commits in dataset: {}",
        event.commits().size(), event.dataset());
  }

  /**
   * Gets the current projection state for a dataset and branch.
   * Returns the commit ID that the branch currently points to.
   *
   * @param dataset the dataset name
   * @param branchName the branch name
   * @return Optional containing the commit ID if the branch exists
   */
  public Optional<CommitId> getProjectionState(String dataset, String branchName) {
    return branchRepository.findByDatasetAndName(dataset, branchName)
        .map(Branch::getCommitId);
  }

  /**
   * Exception thrown when event projection fails.
   */
  public static class ProjectionException extends RuntimeException {
    public ProjectionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
