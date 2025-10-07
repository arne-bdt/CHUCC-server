package org.chucc.vcserver.command;

import java.time.Instant;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfpatch.RDFPatch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.exception.GraphNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.GraphDiffService;
import org.chucc.vcserver.service.PreconditionService;
import org.chucc.vcserver.util.GraphCommandUtil;
import org.springframework.stereotype.Component;

/**
 * Handles DeleteGraphCommand by removing all triples from a graph.
 * Computes DELETE operations for all triples in the graph,
 * and produces a CommitCreatedEvent if changes are non-empty.
 */
@Component
public class DeleteGraphCommandHandler implements CommandHandler<DeleteGraphCommand> {

  private final BranchRepository branchRepository;
  private final DatasetService datasetService;
  private final GraphDiffService graphDiffService;
  private final PreconditionService preconditionService;

  /**
   * Constructs a DeleteGraphCommandHandler.
   *
   * @param branchRepository the branch repository
   * @param datasetService the dataset service
   * @param graphDiffService the graph diff service
   * @param preconditionService the precondition service
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories and services are Spring-managed beans "
          + "and are intentionally shared")
  public DeleteGraphCommandHandler(
      BranchRepository branchRepository,
      DatasetService datasetService,
      GraphDiffService graphDiffService,
      PreconditionService preconditionService) {
    this.branchRepository = branchRepository;
    this.datasetService = datasetService;
    this.graphDiffService = graphDiffService;
    this.preconditionService = preconditionService;
  }

  @Override
  public VersionControlEvent handle(DeleteGraphCommand command) {
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

    // Get current graph state
    Model currentGraph = getCurrentGraph(command);

    // Compute delete diff (DELETE all quads)
    RDFPatch patch = graphDiffService.computeDeleteDiff(
        currentGraph,
        command.isDefaultGraph() ? null : command.graphIri()
    );

    // Check for no-op (per SPARQL 1.2 Protocol: empty patch MUST NOT create commit)
    if (graphDiffService.isPatchEmpty(patch)) {
      return null; // Indicates no-op (graph is already empty)
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

  /**
   * Gets the current graph state from the dataset.
   * Throws GraphNotFoundException if the graph doesn't exist.
   *
   * @param command the DELETE command
   * @return the current graph model
   * @throws GraphNotFoundException if the named graph doesn't exist
   */
  private Model getCurrentGraph(DeleteGraphCommand command) {
    if (command.isDefaultGraph()) {
      return datasetService.getDefaultGraph(
          command.dataset(),
          command.baseCommit()
      );
    } else {
      Model graph = datasetService.getGraph(
          command.dataset(),
          command.baseCommit(),
          command.graphIri()
      );
      // Throw exception if graph doesn't exist (can't delete non-existent graph)
      if (graph == null) {
        throw new GraphNotFoundException(command.graphIri());
      }
      return graph;
    }
  }
}
