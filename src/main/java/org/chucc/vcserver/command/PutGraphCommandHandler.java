package org.chucc.vcserver.command;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfpatch.RDFPatch;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.service.ConflictDetectionService;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.GraphDiffService;
import org.chucc.vcserver.service.PreconditionService;
import org.chucc.vcserver.service.RdfParsingService;
import org.chucc.vcserver.util.GraphCommandUtil;
import org.springframework.stereotype.Component;

/**
 * Handles PutGraphCommand by replacing entire graph content.
 * Computes diff between old and new graph states, creates an RDF Patch,
 * and produces a CommitCreatedEvent if changes are non-empty.
 */
@Component
public class PutGraphCommandHandler implements CommandHandler<PutGraphCommand> {

  private final BranchRepository branchRepository;
  private final DatasetService datasetService;
  private final RdfParsingService rdfParsingService;
  private final GraphDiffService graphDiffService;
  private final PreconditionService preconditionService;
  private final ConflictDetectionService conflictDetectionService;

  /**
   * Constructs a PutGraphCommandHandler.
   *
   * @param branchRepository the branch repository
   * @param datasetService the dataset service
   * @param rdfParsingService the RDF parsing service
   * @param graphDiffService the graph diff service
   * @param preconditionService the precondition service
   * @param conflictDetectionService the conflict detection service
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories and services are Spring-managed beans "
          + "and are intentionally shared")
  public PutGraphCommandHandler(
      BranchRepository branchRepository,
      DatasetService datasetService,
      RdfParsingService rdfParsingService,
      GraphDiffService graphDiffService,
      PreconditionService preconditionService,
      ConflictDetectionService conflictDetectionService) {
    this.branchRepository = branchRepository;
    this.datasetService = datasetService;
    this.rdfParsingService = rdfParsingService;
    this.graphDiffService = graphDiffService;
    this.preconditionService = preconditionService;
    this.conflictDetectionService = conflictDetectionService;
  }

  @Override
  public VersionControlEvent handle(PutGraphCommand command) {
    // Validate branch exists and get current head
    Branch branch = branchRepository
        .findByDatasetAndName(command.dataset(), command.branch())
        .orElseThrow(() -> new IllegalArgumentException(
            "Branch not found: " + command.branch()
                + " in dataset: " + command.dataset()));

    // Check If-Match precondition
    preconditionService.checkIfMatch(
        command.dataset(),
        command.branch(),
        command.ifMatchEtag()
    );

    // Get current graph state (or empty model if graph doesn't exist)
    Model oldGraph = GraphCommandUtil.getCurrentGraph(
        datasetService,
        command.dataset(),
        command.baseCommit(),
        command.graphIri(),
        command.isDefaultGraph()
    );

    // Parse new RDF content
    Model newGraph = rdfParsingService.parseRdf(
        command.rdfContent(),
        command.contentType()
    );

    // Compute diff (DELETE all old quads, ADD all new quads)
    RDFPatch patch = graphDiffService.computePutDiff(
        oldGraph,
        newGraph,
        command.isDefaultGraph() ? null : command.graphIri()
    );

    // Finalize command (check for no-op, conflicts, and create commit event)
    return GraphCommandUtil.finalizeGraphCommand(
        command.dataset(),
        patch,
        graphDiffService,
        conflictDetectionService,
        branch.getCommitId(),
        command.baseCommit(),
        command.message(),
        command.author()
    );
  }
}
