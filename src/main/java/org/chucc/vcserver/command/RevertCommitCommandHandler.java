package org.chucc.vcserver.command;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.RevertCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.springframework.stereotype.Component;

/**
 * Handles RevertCommitCommand by generating a reverse RDF Patch
 * and producing a RevertCreatedEvent.
 */
@Component
public class RevertCommitCommandHandler implements CommandHandler<RevertCommitCommand> {

  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared")
  public RevertCommitCommandHandler(
      BranchRepository branchRepository,
      CommitRepository commitRepository) {
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
  }

  @Override
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "DLS_DEAD_LOCAL_STORE",
      justification = "Branch variable is used for validation and retrieving commit ID")
  public VersionControlEvent handle(RevertCommitCommand command) {
    // Validate branch exists
    Branch branch = branchRepository
        .findByDatasetAndName(command.dataset(), command.branchName())
        .orElseThrow(() -> new IllegalArgumentException(
            "Branch not found: " + command.branchName()
                + " in dataset: " + command.dataset()));

    // Validate commit to revert exists
    CommitId commitToRevert = new CommitId(command.commitId());
    if (!commitRepository.exists(command.dataset(), commitToRevert)) {
      throw new IllegalArgumentException(
          "Commit to revert not found: " + command.commitId()
              + " in dataset: " + command.dataset());
    }

    // Get the patch to revert
    RDFPatch patchToRevert = commitRepository
        .findPatchByDatasetAndId(command.dataset(), commitToRevert)
        .orElseThrow(() -> new IllegalStateException(
            "Patch not found for commit: " + command.commitId()));

    // Generate reverse patch by creating inverse operations
    RDFPatch reversePatch = generateReversePatch(patchToRevert);

    // Generate revert commit ID and message
    CommitId revertCommitId = CommitId.generate();
    String revertMessage = command.message() != null
        ? command.message()
        : "Revert commit " + command.commitId();

    // Serialize reverse patch
    String reversePatchString = serializePatch(reversePatch);

    // Produce event
    return new RevertCreatedEvent(
        command.dataset(),
        revertCommitId.value(),
        command.commitId(),
        revertMessage,
        command.author(),
        Instant.now(),
        reversePatchString);
  }

  /**
   * Generates a reverse patch by swapping add/delete operations.
   *
   * @param originalPatch the original patch
   * @return the reverse patch
   */
  private RDFPatch generateReversePatch(RDFPatch originalPatch) {
    RDFChangesCollector collector = new RDFChangesCollector();

    originalPatch.apply(new org.apache.jena.rdfpatch.RDFChanges() {
      @Override
      public void start() {
        collector.start();
      }

      @Override
      public void finish() {
        collector.finish();
      }

      @Override
      public void header(String field, org.apache.jena.graph.Node value) {
        // Don't copy headers
      }

      @Override
      public void add(org.apache.jena.graph.Node g, org.apache.jena.graph.Node s,
                      org.apache.jena.graph.Node p, org.apache.jena.graph.Node o) {
        // Reverse: add becomes delete
        collector.delete(g, s, p, o);
      }

      @Override
      public void delete(org.apache.jena.graph.Node g, org.apache.jena.graph.Node s,
                        org.apache.jena.graph.Node p, org.apache.jena.graph.Node o) {
        // Reverse: delete becomes add
        collector.add(g, s, p, o);
      }

      @Override
      public void addPrefix(org.apache.jena.graph.Node gn, String prefix, String uriStr) {
        collector.deletePrefix(gn, prefix);
      }

      @Override
      public void deletePrefix(org.apache.jena.graph.Node gn, String prefix) {
        // For reverse, we would need the URI, but we don't have it, so skip
      }

      @Override
      public void txnBegin() {
        collector.txnBegin();
      }

      @Override
      public void txnCommit() {
        collector.txnCommit();
      }

      @Override
      public void txnAbort() {
        collector.txnAbort();
      }

      @Override
      public void segment() {
        collector.segment();
      }
    });

    return collector.getRDFPatch();
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
