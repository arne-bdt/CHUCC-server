package org.chucc.vcserver.command;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.DatasetRef;
import org.chucc.vcserver.dto.ConflictItem;
import org.chucc.vcserver.event.BranchMergedEvent;
import org.chucc.vcserver.event.BranchResetEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.exception.MergeConflictException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.util.CommonAncestorFinder;
import org.chucc.vcserver.util.MergeUtil;
import org.chucc.vcserver.util.RdfPatchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles merge commands with conflict resolution strategies.
 *
 * <p>Implements fast-forward detection, three-way merge with conflict detection,
 * and automatic conflict resolution using "ours" or "theirs" strategies.
 */
@Component
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class MergeCommandHandler implements CommandHandler<MergeCommand> {

  private static final Logger logger = LoggerFactory.getLogger(MergeCommandHandler.class);

  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;
  private final DatasetService datasetService;
  private final EventPublisher eventPublisher;
  private final CommonAncestorFinder ancestorFinder;

  /**
   * Constructs a MergeCommandHandler.
   *
   * @param branchRepository the branch repository
   * @param commitRepository the commit repository
   * @param datasetService the dataset service
   * @param eventPublisher the event publisher
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories and services are Spring-managed beans "
          + "and are intentionally shared")
  public MergeCommandHandler(
      BranchRepository branchRepository,
      CommitRepository commitRepository,
      DatasetService datasetService,
      EventPublisher eventPublisher) {
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
    this.datasetService = datasetService;
    this.eventPublisher = eventPublisher;
    this.ancestorFinder = new CommonAncestorFinder(commitRepository);
  }

  @Override
  public VersionControlEvent handle(MergeCommand command) {
    logger.info("Merging {} into {} in dataset: {}",
        command.from(), command.into(), command.dataset());

    // 1. Resolve refs to commit IDs
    String intoCommitId = resolveRef(command.dataset(), command.into());
    String fromCommitId = resolveRef(command.dataset(), command.from());

    // Check if already up-to-date
    if (intoCommitId.equals(fromCommitId)) {
      logger.info("Branches already at same commit: {}", intoCommitId);
      return null; // No-op merge
    }

    // 2. Check for fast-forward possibility
    boolean canFastForward = ancestorFinder.isAncestor(
        command.dataset(), intoCommitId, fromCommitId);

    if (canFastForward) {
      if ("never".equals(command.fastForward())) {
        // Create merge commit even though fast-forward is possible
        logger.info("Fast-forward possible but forced merge commit requested");
        return createMergeCommit(command, intoCommitId, fromCommitId);
      } else {
        // Fast-forward: just move branch pointer
        logger.info("Fast-forward merge: moving {} to {}", command.into(), fromCommitId);
        return createFastForwardEvent(command, intoCommitId, fromCommitId);
      }
    }

    // 3. Check fast-forward-only mode
    if ("only".equals(command.fastForward())) {
      throw new IllegalStateException(
          "Fast-forward merge not possible (branches have diverged), but fastForward=only");
    }

    // 4. Perform three-way merge
    logger.info("Performing three-way merge");
    return performThreeWayMerge(command, intoCommitId, fromCommitId);
  }

  /**
   * Creates a fast-forward event (BranchResetEvent).
   * No merge commit is created; the branch pointer simply moves forward.
   *
   * @param command the merge command
   * @param fromCommitId the previous commit ID
   * @param toCommitId the new commit ID (target of fast-forward)
   * @return BranchResetEvent
   */
  private VersionControlEvent createFastForwardEvent(
      MergeCommand command, String fromCommitId, String toCommitId) {
    VersionControlEvent event = new BranchResetEvent(
        command.dataset(),
        command.into(),
        fromCommitId,
        toCommitId,
        Instant.now()
    );

    // Publish event to Kafka (async, with proper error logging)
    eventPublisher.publish(event)
        .whenComplete((result, ex) -> {
          if (ex != null) {
            logger.error("Failed to publish event {} to Kafka: {}",
                event.getClass().getSimpleName(), ex.getMessage(), ex);
          } else {
            logger.debug("Successfully published event {} to Kafka",
                event.getClass().getSimpleName());
          }
        });

    logger.info("Fast-forward merge completed: {} -> {}", fromCommitId, toCommitId);
    return event;
  }

  /**
   * Performs a three-way merge with conflict detection.
   *
   * @param command the merge command
   * @param intoCommitId the "into" branch HEAD commit ID
   * @param fromCommitId the "from" ref commit ID
   * @return BranchMergedEvent
   * @throws MergeConflictException if conflicts are detected
   */
  private VersionControlEvent performThreeWayMerge(
      MergeCommand command, String intoCommitId, String fromCommitId) {
    // Find common ancestor
    Optional<String> ancestorIdOpt = ancestorFinder.findCommonAncestor(
        command.dataset(), intoCommitId, fromCommitId);

    if (ancestorIdOpt.isEmpty()) {
      throw new IllegalStateException(
          "No common ancestor found between " + intoCommitId + " and " + fromCommitId);
    }

    String ancestorId = ancestorIdOpt.get();
    logger.debug("Common ancestor: {}", ancestorId);

    // Load three datasets
    Dataset ancestorData = datasetService.getDataset(
        DatasetRef.forCommit(command.dataset(), CommitId.of(ancestorId)));
    Dataset intoData = datasetService.getDataset(
        DatasetRef.forCommit(command.dataset(), CommitId.of(intoCommitId)));
    Dataset fromData = datasetService.getDataset(
        DatasetRef.forCommit(command.dataset(), CommitId.of(fromCommitId)));

    // Compute diffs
    RDFPatch baseToInto = RdfPatchUtil.diff(
        ancestorData.asDatasetGraph(), intoData.asDatasetGraph());
    RDFPatch baseToFrom = RdfPatchUtil.diff(
        ancestorData.asDatasetGraph(), fromData.asDatasetGraph());

    // Detect conflicts
    List<ConflictItem> conflicts = MergeUtil.detectConflicts(baseToInto, baseToFrom);

    RDFPatch mergedPatch;
    int conflictsResolved = 0;

    if (!conflicts.isEmpty()) {
      String strategy = command.strategy();
      String conflictScope = command.conflictScope();

      switch (strategy) {
        case "ours":
          logger.info("Resolving {} conflicts with 'ours' strategy (scope: {})",
              conflicts.size(), conflictScope);
          mergedPatch = MergeUtil.resolveWithOurs(baseToInto, baseToFrom, conflicts, conflictScope);
          conflictsResolved = conflicts.size();
          break;

        case "theirs":
          logger.info("Resolving {} conflicts with 'theirs' strategy (scope: {})",
              conflicts.size(), conflictScope);
          mergedPatch = MergeUtil.resolveWithTheirs(
              baseToInto, baseToFrom, conflicts, conflictScope);
          conflictsResolved = conflicts.size();
          break;

        case "three-way":
        default:
          // Default behavior: throw exception on conflicts
          logger.info("Merge conflicts detected: {} conflicting quad(s)", conflicts.size());
          throw new MergeConflictException(
              "Merge conflicts detected between branches. "
                  + "Use 'ours' or 'theirs' strategy, or resolve manually.",
              conflicts);
      }
    } else {
      // No conflicts: combine both patches
      logger.info("No conflicts detected, combining patches");
      mergedPatch = combineDiffs(baseToInto, baseToFrom);
    }

    return createMergeCommit(command, intoCommitId, fromCommitId, mergedPatch, conflictsResolved);
  }

  /**
   * Creates a merge commit combining changes from both branches.
   *
   * @param command the merge command
   * @param intoCommitId the "into" branch HEAD commit ID
   * @param fromCommitId the "from" ref commit ID
   * @return BranchMergedEvent
   */
  private VersionControlEvent createMergeCommit(
      MergeCommand command, String intoCommitId, String fromCommitId) {
    // Load both commits' data
    Dataset intoData = datasetService.getDataset(
        DatasetRef.forCommit(command.dataset(), CommitId.of(intoCommitId)));
    Dataset fromData = datasetService.getDataset(
        DatasetRef.forCommit(command.dataset(), CommitId.of(fromCommitId)));

    // Compute diff from "into" to "from"
    RDFPatch mergePatch = RdfPatchUtil.diff(
        intoData.asDatasetGraph(), fromData.asDatasetGraph());

    return createMergeCommit(command, intoCommitId, fromCommitId, mergePatch, 0);
  }

  /**
   * Creates a merge commit with the given merged patch and conflict count.
   *
   * @param command the merge command
   * @param intoCommitId the "into" branch HEAD commit ID
   * @param fromCommitId the "from" ref commit ID
   * @param mergePatch the merged RDF patch
   * @param conflictsResolved number of conflicts that were auto-resolved
   * @return BranchMergedEvent
   */
  private VersionControlEvent createMergeCommit(
      MergeCommand command, String intoCommitId, String fromCommitId,
      RDFPatch mergePatch, int conflictsResolved) {
    int patchSize = RdfPatchUtil.countOperations(mergePatch);

    // Generate merge commit ID
    CommitId mergeCommitId = CommitId.generate();

    // Serialize patch to string
    String patchString = serializePatch(mergePatch);

    // Generate merge commit message
    String message = command.message() != null
        ? command.message()
        : "Merge " + command.from() + " into " + command.into();

    // Produce event
    VersionControlEvent event = new BranchMergedEvent(
        null,  // eventId - auto-generated
        command.dataset(),
        command.into(),
        command.from(),
        mergeCommitId.value(),
        List.of(intoCommitId, fromCommitId),
        message,
        command.author(),
        Instant.now(),
        patchString,
        patchSize,
        conflictsResolved
    );

    // Publish event to Kafka (async, with proper error logging)
    eventPublisher.publish(event)
        .whenComplete((result, ex) -> {
          if (ex != null) {
            logger.error("Failed to publish event {} to Kafka: {}",
                event.getClass().getSimpleName(), ex.getMessage(), ex);
          } else {
            logger.debug("Successfully published event {} to Kafka",
                event.getClass().getSimpleName());
          }
        });

    logger.info("Merge commit created: {} (parents: {}, {})",
        mergeCommitId, intoCommitId, fromCommitId);
    return event;
  }

  /**
   * Resolves a ref (branch name or commit ID) to a commit ID.
   *
   * @param dataset the dataset name
   * @param ref the ref to resolve
   * @return the resolved commit ID
   * @throws IllegalArgumentException if the ref cannot be resolved
   */
  private String resolveRef(String dataset, String ref) {
    // Try as branch first
    Optional<Branch> branch = branchRepository.findByDatasetAndName(dataset, ref);
    if (branch.isPresent()) {
      return branch.get().getCommitId().value();
    }

    // Try as commit ID directly
    try {
      CommitId commitId = CommitId.of(ref);
      if (commitRepository.exists(dataset, commitId)) {
        return ref;
      }
    } catch (IllegalArgumentException e) {
      // Not a valid commit ID format
    }

    throw new IllegalArgumentException("Ref not found: " + ref);
  }

  /**
   * Combines two RDF patches into a single patch.
   * Used when there are no conflicts - simply applies both patches in sequence.
   *
   * @param patch1 first patch to combine
   * @param patch2 second patch to combine
   * @return combined patch
   */
  private RDFPatch combineDiffs(RDFPatch patch1, RDFPatch patch2) {
    org.apache.jena.rdfpatch.changes.RDFChangesCollector collector =
        new org.apache.jena.rdfpatch.changes.RDFChangesCollector();
    collector.start();

    // Apply first patch
    patch1.apply(collector);

    // Apply second patch
    patch2.apply(collector);

    collector.finish();
    return collector.getRDFPatch();
  }

  /**
   * Serializes an RDFPatch to a string.
   *
   * @param patch the patch to serialize
   * @return the serialized patch
   */
  private String serializePatch(RDFPatch patch) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    RDFPatchOps.write(out, patch);
    return out.toString(java.nio.charset.StandardCharsets.UTF_8);
  }
}
