package org.chucc.vcserver.projection;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.BranchCreatedEvent;
import org.chucc.vcserver.event.BranchResetEvent;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.RevertCreatedEvent;
import org.chucc.vcserver.event.SnapshotCreatedEvent;
import org.chucc.vcserver.event.TagCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
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
public class ReadModelProjector {
  private static final Logger logger = LoggerFactory.getLogger(ReadModelProjector.class);

  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;
  private final SnapshotService snapshotService;

  /**
   * Constructs a ReadModelProjector.
   *
   * @param branchRepository the branch repository
   * @param commitRepository the commit repository
   * @param snapshotService the snapshot service
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared")
  public ReadModelProjector(
      BranchRepository branchRepository,
      CommitRepository commitRepository,
      SnapshotService snapshotService) {
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
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
      groupId = "read-model-projector",
      containerFactory = "kafkaListenerContainerFactory"
  )
  public void handleEvent(VersionControlEvent event) {
    logger.debug("Received event: {} for dataset: {}",
        event.getClass().getSimpleName(), event.dataset());

    try {
      switch (event) {
        case CommitCreatedEvent e -> handleCommitCreated(e);
        case BranchCreatedEvent e -> handleBranchCreated(e);
        case BranchResetEvent e -> handleBranchReset(e);
        case TagCreatedEvent e -> handleTagCreated(e);
        case RevertCreatedEvent e -> handleRevertCreated(e);
        case SnapshotCreatedEvent e -> handleSnapshotCreated(e);
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
   *
   * @param event the commit created event
   */
  void handleCommitCreated(CommitCreatedEvent event) {
    logger.debug("Processing CommitCreatedEvent: commitId={}, dataset={}",
        event.commitId(), event.dataset());

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
            + "dataset={}",
        event.revertCommitId(), event.revertedCommitId(), event.dataset());

    // Parse RDF Patch from string
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        event.rdfPatch().getBytes(StandardCharsets.UTF_8));
    RDFPatch patch = RDFPatchOps.read(inputStream);

    // Find the reverted commit to get parent information
    Commit revertedCommit = commitRepository.findByDatasetAndId(
        event.dataset(),
        CommitId.of(event.revertedCommitId()))
        .orElseThrow(() -> new IllegalArgumentException(
            "Cannot revert non-existent commit: " + event.revertedCommitId()));

    // Create Commit domain object for revert
    // The revert commit's parent is the current HEAD (the commit being reverted from)
    Commit commit = new Commit(
        CommitId.of(event.revertCommitId()),
        revertedCommit.parents(), // Use the parent(s) from the reverted commit
        event.author(),
        event.message(),
        event.timestamp()
    );

    // Save revert commit and patch
    commitRepository.save(event.dataset(), commit, patch);

    logger.debug("Saved revert commit: {} reverting {} in dataset: {}",
        commit.id(), event.revertedCommitId(), event.dataset());
  }

  /**
   * Handles SnapshotCreatedEvent (currently logs only, can be extended for snapshot recovery).
   * Snapshots can be used to speed up recovery by loading a checkpoint instead of
   * replaying all events from the beginning.
   *
   * @param event the snapshot created event
   */
  void handleSnapshotCreated(SnapshotCreatedEvent event) {
    logger.debug("Processing SnapshotCreatedEvent: branchName={}, commitId={}, dataset={}",
        event.branchName(), event.commitId(), event.dataset());

    // Snapshot handling can be implemented for faster recovery
    // For now, we just log that a snapshot was created
    logger.info("Snapshot created for branch: {} at commit: {} in dataset: {}",
        event.branchName(), event.commitId(), event.dataset());
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
