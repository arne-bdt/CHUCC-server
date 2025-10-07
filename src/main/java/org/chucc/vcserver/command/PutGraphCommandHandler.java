package org.chucc.vcserver.command;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.GraphDiffService;
import org.chucc.vcserver.service.PreconditionService;
import org.chucc.vcserver.service.RdfParsingService;
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

  /**
   * Constructs a PutGraphCommandHandler.
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
  public PutGraphCommandHandler(
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
  public VersionControlEvent handle(PutGraphCommand command) {
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
    Model oldGraph = getCurrentGraph(command);

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

    // Check for no-op (per SPARQL 1.2 Protocol: empty patch MUST NOT create commit)
    if (graphDiffService.isPatchEmpty(patch)) {
      return null; // Indicates no-op
    }

    // Generate commit ID
    CommitId commitId = CommitId.generate();

    // Serialize patch to string
    String patchString = serializePatch(patch);

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
   * Returns an empty model if the graph doesn't exist (for new graph creation).
   *
   * @param command the PUT command
   * @return the current graph model, or empty model if graph doesn't exist
   */
  private Model getCurrentGraph(PutGraphCommand command) {
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
      // Return empty model if graph doesn't exist (creating new graph)
      return graph != null ? graph : ModelFactory.createDefaultModel();
    }
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
