package org.chucc.vcserver.command;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.time.Instant;
import java.util.List;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.mem.DatasetGraphInMemory;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.util.RdfPatchUtil;
import org.springframework.stereotype.Component;

/**
 * Handles CreateCommitCommand by executing the SPARQL Update,
 * generating an RDF Patch, and producing a CommitCreatedEvent.
 * Includes optimistic concurrency control via patch intersection.
 */
@Component
public class CreateCommitCommandHandler implements CommandHandler<CreateCommitCommand> {

  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;
  private final DatasetService datasetService;

  /**
   * Constructs a CreateCommitCommandHandler.
   *
   * @param branchRepository the branch repository
   * @param commitRepository the commit repository
   * @param datasetService the dataset service
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories and services are Spring-managed beans "
          + "and are intentionally shared")
  public CreateCommitCommandHandler(
      BranchRepository branchRepository,
      CommitRepository commitRepository,
      DatasetService datasetService) {
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
    this.datasetService = datasetService;
  }

  @Override
  public VersionControlEvent handle(CreateCommitCommand command) {
    // Validate branch exists
    Branch branch = branchRepository
        .findByDatasetAndName(command.dataset(), command.branchName())
        .orElseThrow(() -> new IllegalArgumentException(
            "Branch not found: " + command.branchName()
                + " in dataset: " + command.dataset()));

    CommitId parentCommitId = branch.getCommitId();

    // Validate parent commit exists
    Commit parentCommit = commitRepository
        .findByDatasetAndId(command.dataset(), parentCommitId)
        .orElseThrow(() -> new IllegalStateException(
            "Parent commit not found: " + parentCommitId
                + " in dataset: " + command.dataset()));

    // Get parent commit's patch for optimistic concurrency check
    RDFPatch parentPatch = commitRepository
        .findPatchByDatasetAndId(command.dataset(), parentCommitId)
        .orElse(RDFPatchOps.emptyPatch());

    // Execute SPARQL Update and generate patch
    RDFPatch newPatch = executeSparqlUpdateAndGeneratePatch(
        command.dataset(),
        parentCommitId,
        command.sparqlUpdate());

    // Optimistic concurrency control: check for conflicts
    // If the parent has changes and the new patch intersects with it, reject
    if (!parentCommit.isInitial() && PatchIntersection.intersects(parentPatch, newPatch)) {
      throw new IllegalStateException(
          "Conflict detected: changes overlap with parent commit. "
              + "Please rebase or merge before committing.");
    }

    // Generate commit ID
    CommitId commitId = CommitId.generate();

    // Serialize patch to string
    String patchString = serializePatch(newPatch);

    // Produce event
    return new CommitCreatedEvent(
        command.dataset(),
        commitId.value(),
        List.of(parentCommitId.value()),
        command.message(),
        command.author(),
        Instant.now(),
        patchString);
  }

  /**
   * Executes a SPARQL Update on a copy of the parent dataset
   * and generates an RDF Patch representing the changes.
   *
   * @param datasetName the dataset name
   * @param parentCommitId the parent commit ID
   * @param sparqlUpdate the SPARQL Update query
   * @return the RDF Patch representing the changes
   */
  private RDFPatch executeSparqlUpdateAndGeneratePatch(
      String datasetName,
      CommitId parentCommitId,
      String sparqlUpdate) {

    // Get the parent dataset state
    Dataset parentDataset = datasetService.getDataset(
        new org.chucc.vcserver.domain.DatasetRef(datasetName, parentCommitId.value()));

    DatasetGraph sourceGraph = parentDataset.asDatasetGraph();

    // Create a copy for modification
    DatasetGraph targetGraph = new DatasetGraphInMemory();
    RDFPatchOps.applyChange(targetGraph, RdfPatchUtil.diff(
        new DatasetGraphInMemory(), sourceGraph));

    // Execute SPARQL Update on the copy
    try {
      UpdateRequest updateRequest = UpdateFactory.create(sparqlUpdate);
      UpdateAction.execute(updateRequest, targetGraph);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Failed to execute SPARQL Update: " + e.getMessage(), e);
    }

    // Generate diff patch between source and target
    return RdfPatchUtil.diff(sourceGraph, targetGraph);
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
