package org.chucc.vcserver.command;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfpatch.RDFPatch;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.service.ConflictDetectionService;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.GraphDiffService;
import org.chucc.vcserver.service.PreconditionService;
import org.chucc.vcserver.service.RdfParsingService;
import org.chucc.vcserver.util.GraphCommandUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles PostGraphCommand by merging RDF content into a graph.
 * Computes additive diff (only ADD operations for new triples),
 * and produces a CommitCreatedEvent if changes are non-empty.
 */
@Component
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class PostGraphCommandHandler implements CommandHandler<PostGraphCommand> {

  private static final Logger logger = LoggerFactory.getLogger(PostGraphCommandHandler.class);

  private final EventPublisher eventPublisher;
  private final BranchRepository branchRepository;
  private final DatasetService datasetService;
  private final RdfParsingService rdfParsingService;
  private final GraphDiffService graphDiffService;
  private final PreconditionService preconditionService;
  private final ConflictDetectionService conflictDetectionService;

  /**
   * Constructs a PostGraphCommandHandler.
   *
   * @param eventPublisher the event publisher
   * @param branchRepository the branch repository
   * @param datasetService the dataset service
   * @param rdfParsingService the RDF parsing service
   * @param graphDiffService the graph diff service
   * @param preconditionService the precondition validation service
   * @param conflictDetectionService the conflict detection service
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories and services are Spring-managed beans "
          + "and are intentionally shared")
  public PostGraphCommandHandler(
      EventPublisher eventPublisher,
      BranchRepository branchRepository,
      DatasetService datasetService,
      RdfParsingService rdfParsingService,
      GraphDiffService graphDiffService,
      PreconditionService preconditionService,
      ConflictDetectionService conflictDetectionService) {
    this.eventPublisher = eventPublisher;
    this.branchRepository = branchRepository;
    this.datasetService = datasetService;
    this.rdfParsingService = rdfParsingService;
    this.graphDiffService = graphDiffService;
    this.preconditionService = preconditionService;
    this.conflictDetectionService = conflictDetectionService;
  }

  @Override
  public VersionControlEvent handle(PostGraphCommand command) {
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

    // Finalize command and publish event to Kafka
    return GraphCommandUtil.finalizeAndPublishGraphCommand(
        command.dataset(),
        patch,
        graphDiffService,
        conflictDetectionService,
        branch.getCommitId(),
        command.baseCommit(),
        command.message(),
        command.author(),
        eventPublisher,
        logger
    );
  }
}
