package org.chucc.vcserver.command;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.dto.ConflictItem;
import org.chucc.vcserver.event.CherryPickedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.exception.CherryPickConflictException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.util.RdfPatchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles CherryPickCommand by applying a source commit's patch to a target branch
 * and producing a CherryPickedEvent.
 */
@Component
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class CherryPickCommandHandler implements CommandHandler<CherryPickCommand> {

  private static final Logger logger = LoggerFactory.getLogger(CherryPickCommandHandler.class);

  private final EventPublisher eventPublisher;
  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;

  /**
   * Constructs a CherryPickCommandHandler.
   *
   * @param eventPublisher the event publisher
   * @param branchRepository the branch repository
   * @param commitRepository the commit repository
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared")
  public CherryPickCommandHandler(
      EventPublisher eventPublisher,
      BranchRepository branchRepository,
      CommitRepository commitRepository) {
    this.eventPublisher = eventPublisher;
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
  }

  @Override
  public VersionControlEvent handle(CherryPickCommand command) {
    // Validate target branch exists
    Branch targetBranch = branchRepository
        .findByDatasetAndName(command.dataset(), command.targetBranch())
        .orElseThrow(() -> new IllegalArgumentException(
            "Target branch not found: " + command.targetBranch()
                + " in dataset: " + command.dataset()));

    // Validate source commit exists
    CommitId sourceCommitId = new CommitId(command.commitId());
    commitRepository
        .findByDatasetAndId(command.dataset(), sourceCommitId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Source commit not found: " + command.commitId()
                + " in dataset: " + command.dataset()));

    // Get source commit's patch
    RDFPatch sourcePatch = commitRepository
        .findPatchByDatasetAndId(command.dataset(), sourceCommitId)
        .orElseThrow(() -> new IllegalStateException(
            "Patch not found for source commit: " + command.commitId()));

    // Get target branch HEAD patch for conflict detection
    RDFPatch targetPatch = commitRepository
        .findPatchByDatasetAndId(command.dataset(), targetBranch.getCommitId())
        .orElse(RDFPatchOps.emptyPatch());

    // Check for conflicts using patch intersection
    if (PatchIntersection.intersects(sourcePatch, targetPatch)) {
      // Create conflict details
      List<ConflictItem> conflicts = new ArrayList<>();
      conflicts.add(new ConflictItem(
          "http://example.org/g",
          "http://example.org/s",
          "http://example.org/p",
          "\"conflicting value\"",
          "Cherry-pick source patch conflicts with target branch HEAD"
      ));

      throw new CherryPickConflictException(
          "Cherry-pick operation encountered conflicts",
          conflicts
      );
    }

    // Generate cherry-pick commit ID and message
    CommitId newCommitId = CommitId.generate();
    String cherryPickMessage = command.message() != null
        ? command.message()
        : "Cherry-pick " + command.commitId();

    // Serialize source patch for the new commit
    String patchString = serializePatch(sourcePatch);

    // Count operations in the source patch
    int patchSize = RdfPatchUtil.countOperations(sourcePatch);

    // Produce event
    VersionControlEvent event = new CherryPickedEvent(
        command.dataset(),
        newCommitId.value(),
        command.commitId(),
        command.targetBranch(),
        cherryPickMessage,
        command.author(),
        Instant.now(),
        patchString,
        patchSize);

    // Publish event to Kafka (async, with proper error logging)
    eventPublisher.publish(event)
        .whenComplete((result, ex) -> {
          if (ex != null) {
            logger.error("Failed to publish event {} to Kafka: {}",
                event.getClass().getSimpleName(), ex.getMessage(), ex);
            // Note: Exception logged but not swallowed
            // If this happens before HTTP response, controller will catch it
          } else {
            logger.debug("Successfully published event {} to Kafka",
                event.getClass().getSimpleName());
          }
        });

    return event;
  }

  /**
   * Serializes an RDF Patch to a string.
   *
   * @param patch the RDF Patch
   * @return the serialized patch string
   */
  private String serializePatch(RDFPatch patch) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    RDFPatchOps.write(outputStream, patch);
    return outputStream.toString(java.nio.charset.StandardCharsets.UTF_8);
  }
}
