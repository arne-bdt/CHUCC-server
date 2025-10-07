package org.chucc.vcserver.command;

import java.time.Instant;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfpatch.RDFPatch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.GraphDiffService;
import org.chucc.vcserver.service.PreconditionService;
import org.chucc.vcserver.service.RdfParsingService;
import org.chucc.vcserver.util.GraphCommandUtil;
import org.springframework.stereotype.Component;

/**
 * Handles PostGraphCommand by merging RDF content into a graph.
 * Computes additive diff (only ADD operations for new triples),
 * and produces a CommitCreatedEvent if changes are non-empty.
 */
@Component
public class PostGraphCommandHandler implements CommandHandler<PostGraphCommand> {

  private final BranchRepository branchRepository;
  private final DatasetService datasetService;
  private final RdfParsingService rdfParsingService;
  private final GraphDiffService graphDiffService;
  private final PreconditionService preconditionService;

  /**
   * Constructs a PostGraphCommandHandler.
   *
   * @param branchRepository the branch repository
   * @param datasetService the dataset service
   * @param rdfParsingService the RDF parsing service
   * @param graphDiffService the graph diff service
   * @param preconditionService the precondition service
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories and services are Spring-managed beans "
          + "and are intentionally shared")
  public PostGraphCommandHandler(
      BranchRepository branchRepository,
      DatasetService datasetService,
      RdfParsingService rdfParsingService,
      GraphDiffService graphDiffService,
      PreconditionService preconditionService) {
    this.branchRepository = branchRepository;
    this.datasetService = datasetService;
    this.rdfParsingService = rdfParsingService;
    this.graphDiffService = graphDiffService;
    this.preconditionService = preconditionService;
  }

  @Override
  public VersionControlEvent handle(PostGraphCommand command) {
    // Validate branch exists
    branchRepository
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
    Model currentGraph = GraphCommandUtil.getCurrentGraph(
        datasetService,
        command.dataset(),
        command.baseCommit(),
        command.graphIri(),
        command.isDefaultGraph()
    );

    // Parse new RDF content
    Model newContent = rdfParsingService.parseRdf(
        command.rdfContent(),
        command.contentType()
    );

    // Compute additive diff (only ADD operations for new triples)
    RDFPatch patch = graphDiffService.computePostDiff(
        currentGraph,
        newContent,
        command.isDefaultGraph() ? null : command.graphIri()
    );

    // Check for no-op (per SPARQL 1.2 Protocol: empty patch MUST NOT create commit)
    if (graphDiffService.isPatchEmpty(patch)) {
      return null; // Indicates no-op
    }

    // Generate commit ID
    CommitId commitId = CommitId.generate();

    // Serialize patch to string
    String patchString = GraphCommandUtil.serializePatch(patch);

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
