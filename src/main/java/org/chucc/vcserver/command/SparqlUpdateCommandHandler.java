package org.chucc.vcserver.command;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.mem.DatasetGraphInMemory;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.exception.MalformedUpdateException;
import org.chucc.vcserver.exception.PreconditionFailedException;
import org.chucc.vcserver.exception.UpdateExecutionException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.service.ConflictDetectionService;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.GraphDiffService;
import org.chucc.vcserver.service.PreconditionService;
import org.chucc.vcserver.util.GraphCommandUtil;
import org.chucc.vcserver.util.RdfPatchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Command handler for SPARQL UPDATE operations.
 *
 * <p>This handler processes SPARQL UPDATE commands that modify RDF graphs
 * through INSERT DATA, DELETE DATA, INSERT/DELETE WHERE, and other SPARQL
 * Update operations. Each successful update creates a version control commit.</p>
 *
 * <p>The handler follows these steps:</p>
 * <ol>
 *   <li>Resolve branch HEAD commit</li>
 *   <li>Check If-Match precondition (if specified)</li>
 *   <li>Materialize dataset at HEAD</li>
 *   <li>Clone dataset for before/after comparison</li>
 *   <li>Execute SPARQL UPDATE on the clone</li>
 *   <li>Compute RDF diff between before and after states</li>
 *   <li>Check for no-op (empty patch)</li>
 *   <li>Detect conflicts with concurrent writes</li>
 *   <li>Publish CommitCreatedEvent to Kafka</li>
 * </ol>
 *
 * <p>Per SPARQL 1.2 Protocol §7, updates that result in no semantic change
 * (no-op patches) MUST NOT create commits. The handler returns null for no-ops.</p>
 *
 * @see SparqlUpdateCommand
 * @see MalformedUpdateException
 * @see UpdateExecutionException
 */
@Component
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class SparqlUpdateCommandHandler implements CommandHandler<SparqlUpdateCommand> {

  private static final Logger logger =
      LoggerFactory.getLogger(SparqlUpdateCommandHandler.class);

  private final EventPublisher eventPublisher;
  private final BranchRepository branchRepository;
  private final DatasetService datasetService;
  private final GraphDiffService graphDiffService;
  private final ConflictDetectionService conflictDetectionService;
  private final PreconditionService preconditionService;

  /**
   * Constructs a new SPARQL UPDATE command handler.
   *
   * @param eventPublisher the event publisher for Kafka
   * @param branchRepository the branch repository
   * @param datasetService the dataset service
   * @param graphDiffService the graph diff service
   * @param conflictDetectionService the conflict detection service
   * @param preconditionService the precondition service
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Services are Spring-managed beans and are intentionally shared")
  public SparqlUpdateCommandHandler(
      EventPublisher eventPublisher,
      BranchRepository branchRepository,
      DatasetService datasetService,
      GraphDiffService graphDiffService,
      ConflictDetectionService conflictDetectionService,
      PreconditionService preconditionService) {
    this.eventPublisher = eventPublisher;
    this.branchRepository = branchRepository;
    this.datasetService = datasetService;
    this.graphDiffService = graphDiffService;
    this.conflictDetectionService = conflictDetectionService;
    this.preconditionService = preconditionService;
  }

  @Override
  public VersionControlEvent handle(SparqlUpdateCommand command) {
    // 1. Resolve branch HEAD
    Branch branch = branchRepository
        .findByDatasetAndName(command.dataset(), command.branch())
        .orElseThrow(() -> new IllegalArgumentException(
            "Branch not found: " + command.branch()
                + " in dataset: " + command.dataset()));

    CommitId currentHead = branch.getCommitId();

    // 2. Check If-Match precondition (if specified)
    if (command.expectedHead().isPresent()) {
      CommitId expectedHead = command.expectedHead().get();
      if (!currentHead.equals(expectedHead)) {
        throw new PreconditionFailedException(
            expectedHead.value(),
            currentHead.value());
      }
    }

    // 3. Materialize dataset at HEAD
    DatasetGraph currentDataset = datasetService.materializeCommit(
        command.dataset(), currentHead);

    // 4. Clone dataset for modification
    DatasetGraph modifiedDataset = cloneDataset(currentDataset);

    // 5. Execute SPARQL UPDATE
    try {
      UpdateRequest updateRequest = UpdateFactory.create(command.updateString());
      UpdateAction.execute(updateRequest, modifiedDataset);
    } catch (org.apache.jena.query.QueryParseException e) {
      throw new MalformedUpdateException(
          "SPARQL UPDATE query is malformed: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new UpdateExecutionException(
          "Failed to execute SPARQL UPDATE: " + e.getMessage(), e);
    }

    // 6. Compute RDF diff
    RDFPatch patch = RdfPatchUtil.diff(currentDataset, modifiedDataset);

    // 7. Check for no-op (per §7: empty patch MUST NOT create commit)
    if (RdfPatchUtil.isNoOp(patch, currentDataset)) {
      logger.info("SPARQL UPDATE resulted in no-op for dataset={}, branch={}",
          command.dataset(), command.branch());
      return null; // No-op indicator
    }

    // 8. Finalize command and publish event
    return GraphCommandUtil.finalizeAndPublishGraphCommand(
        command.dataset(),
        command.branch(),
        patch,
        graphDiffService,
        conflictDetectionService,
        currentHead,
        currentHead, // baseCommit = currentHead for SPARQL UPDATE
        command.message(),
        command.author(),
        eventPublisher,
        logger
    );
  }

  /**
   * Clones a DatasetGraph by copying all quads.
   * Creates a new in-memory dataset with the same content.
   *
   * @param source the source dataset to clone
   * @return a new dataset containing all quads from the source
   */
  private DatasetGraph cloneDataset(DatasetGraph source) {
    DatasetGraph clone = new DatasetGraphInMemory();

    // Copy all quads using RDF Patch (efficient and preserves structure)
    RDFPatch copyPatch = RdfPatchUtil.diff(new DatasetGraphInMemory(), source);
    RDFPatchOps.applyChange(clone, copyPatch);

    return clone;
  }
}
