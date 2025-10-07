package org.chucc.vcserver.command;

import java.time.Instant;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfpatch.RDFPatch;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.service.ConflictDetectionService;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.GraphDiffService;
import org.chucc.vcserver.service.PreconditionService;
import org.chucc.vcserver.service.RdfPatchService;
import org.chucc.vcserver.util.GraphCommandUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Handles PatchGraphCommand by applying an RDF Patch to a graph.
 * Validates patch syntax and applicability before creating a commit.
 */
@Component
public class PatchGraphCommandHandler implements CommandHandler<PatchGraphCommand> {

  private final BranchRepository branchRepository;
  private final DatasetService datasetService;
  private final RdfPatchService rdfPatchService;
  private final GraphDiffService graphDiffService;
  private final PreconditionService preconditionService;
  private final ConflictDetectionService conflictDetectionService;

  /**
   * Constructs a PatchGraphCommandHandler.
   *
   * @param branchRepository the branch repository
   * @param datasetService the dataset service
   * @param rdfPatchService the RDF patch service
   * @param graphDiffService the graph diff service
   * @param preconditionService the precondition validation service
   * @param conflictDetectionService the conflict detection service
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories and services are Spring-managed beans "
          + "and are intentionally shared")
  public PatchGraphCommandHandler(
      BranchRepository branchRepository,
      DatasetService datasetService,
      RdfPatchService rdfPatchService,
      GraphDiffService graphDiffService,
      PreconditionService preconditionService,
      ConflictDetectionService conflictDetectionService) {
    this.branchRepository = branchRepository;
    this.datasetService = datasetService;
    this.rdfPatchService = rdfPatchService;
    this.graphDiffService = graphDiffService;
    this.preconditionService = preconditionService;
    this.conflictDetectionService = conflictDetectionService;
  }

  @Override
  public VersionControlEvent handle(PatchGraphCommand command) {
    // Validate branch exists and get current head
    Branch branch = branchRepository
        .findByDatasetAndName(command.dataset(), command.branch())
        .orElseThrow(() -> new IllegalArgumentException(
            "Branch not found: " + command.branch()
                + " in dataset: " + command.dataset()));

    // Check HTTP precondition (If-Match header) against graph-level ETag - returns 412 if mismatch
    preconditionService.checkIfMatchForGraph(
        command.dataset(),
        command.branch(),
        command.graphIri(),
        command.ifMatchEtag()
    );

    // Parse RDF Patch (throws 400 on syntax errors)
    RDFPatch patch = rdfPatchService.parsePatch(command.patchContent());

    // Filter patch to target graph only
    String graphIri = command.isDefaultGraph() ? null : command.graphIri();
    RDFPatch filteredPatch = rdfPatchService.filterByGraph(patch, graphIri);

    // Get current graph state (or empty model if graph doesn't exist)
    Model currentGraph = GraphCommandUtil.getCurrentGraph(
        datasetService,
        command.dataset(),
        command.baseCommit(),
        command.graphIri(),
        command.isDefaultGraph()
    );

    // Validate patch can be applied (throws 422 on semantic errors)
    if (!rdfPatchService.canApply(currentGraph, filteredPatch)) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "Patch cannot be applied to current graph state. "
              + "DELETE operations may reference non-existent triples."
      );
    }

    // Check for no-op (per SPARQL 1.2 Protocol: empty patch MUST NOT create commit)
    if (graphDiffService.isPatchEmpty(filteredPatch)) {
      return null; // Indicates no-op
    }

    // Check for concurrent writes
    conflictDetectionService.checkForConcurrentWrites(command.dataset(), branch.getCommitId(),
        command.baseCommit(), filteredPatch);

    // Generate commit ID
    CommitId commitId = CommitId.generate();

    // Serialize filtered patch to string
    String patchString = GraphCommandUtil.serializePatch(filteredPatch);

    // Produce event
    return new CommitCreatedEvent(
        command.dataset(),
        commitId.value(),
        List.of(command.baseCommit().value()),
        command.message(),
        command.author(),
        Instant.now(),
        patchString
    );
  }
}
