package org.chucc.vcserver.command;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.chucc.vcserver.command.BatchGraphsCommand.GraphOperation;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.BatchGraphsCompletedEvent;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.service.ConflictDetectionService;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.GraphDiffService;
import org.chucc.vcserver.service.RdfParsingService;
import org.chucc.vcserver.service.RdfPatchService;
import org.chucc.vcserver.util.GraphCommandUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles BatchGraphsCommand by executing multiple graph operations atomically.
 * Supports two modes:
 * - "single": All operations combined into one commit
 * - "multiple": Each operation creates a separate commit
 */
@Component
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class BatchGraphsCommandHandler implements CommandHandler<BatchGraphsCommand> {

  private static final Logger logger = LoggerFactory.getLogger(BatchGraphsCommandHandler.class);

  private final EventPublisher eventPublisher;
  private final BranchRepository branchRepository;
  private final DatasetService datasetService;
  private final RdfParsingService rdfParsingService;
  private final RdfPatchService rdfPatchService;
  private final GraphDiffService graphDiffService;
  private final ConflictDetectionService conflictDetectionService;

  /**
   * Constructs a BatchGraphsCommandHandler.
   *
   * @param eventPublisher the event publisher
   * @param branchRepository the branch repository
   * @param datasetService the dataset service
   * @param rdfParsingService the RDF parsing service
   * @param rdfPatchService the RDF Patch parsing service
   * @param graphDiffService the graph diff service
   * @param conflictDetectionService the conflict detection service
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories and services are Spring-managed beans "
          + "and are intentionally shared")
  public BatchGraphsCommandHandler(
      EventPublisher eventPublisher,
      BranchRepository branchRepository,
      DatasetService datasetService,
      RdfParsingService rdfParsingService,
      RdfPatchService rdfPatchService,
      GraphDiffService graphDiffService,
      ConflictDetectionService conflictDetectionService) {
    this.eventPublisher = eventPublisher;
    this.branchRepository = branchRepository;
    this.datasetService = datasetService;
    this.rdfParsingService = rdfParsingService;
    this.rdfPatchService = rdfPatchService;
    this.graphDiffService = graphDiffService;
    this.conflictDetectionService = conflictDetectionService;
  }

  @Override
  public VersionControlEvent handle(BatchGraphsCommand command) {
    // Validate branch exists
    Branch branch = branchRepository
        .findByDatasetAndName(command.dataset(), command.branch())
        .orElseThrow(() -> new IllegalArgumentException(
            "Branch not found: " + command.branch()
                + " in dataset: " + command.dataset()));

    VersionControlEvent event;
    if ("single".equals(command.mode())) {
      event = handleSingleMode(command, branch);
    } else {
      event = handleMultipleMode(command, branch);
    }

    // Publish event to Kafka (fire-and-forget, async)
    if (event != null) {
      eventPublisher.publish(event)
          .exceptionally(ex -> {
            logger.error("Failed to publish event {}: {}",
                event.getClass().getSimpleName(), ex.getMessage(), ex);
            return null;
          });
    }

    return event;
  }

  private VersionControlEvent handleSingleMode(BatchGraphsCommand command, Branch branch) {
    List<RDFPatch> patches = new ArrayList<>();

    // Execute all operations and collect patches
    for (GraphOperation operation : command.operations()) {
      RDFPatch patch = executeOperation(
          operation,
          command.dataset(),
          command.baseCommit()
      );
      patches.add(patch);
    }

    // Combine all patches
    RDFPatch combinedPatch = combinePatches(patches);

    // Check for no-op
    if (graphDiffService.isPatchEmpty(combinedPatch)) {
      return null; // No-op result
    }

    // Check for conflicts
    conflictDetectionService.checkForConcurrentWrites(
        command.dataset(),
        branch.getCommitId(),
        command.baseCommit(),
        combinedPatch
    );

    // Create single commit
    CommitId commitId = CommitId.generate();
    String patchString = serializePatch(combinedPatch);

    CommitCreatedEvent commitEvent = new CommitCreatedEvent(
        command.dataset(),
        commitId.value(),
        List.of(command.baseCommit().value()),
        command.branch(),
        command.message(),
        command.author(),
        Instant.now(),
        patchString
    );

    return new BatchGraphsCompletedEvent(
        command.dataset(),
        List.of(commitEvent),
        Instant.now()
    );
  }

  private VersionControlEvent handleMultipleMode(BatchGraphsCommand command, Branch branch) {
    record OperationResult(int operationIndex, GraphOperation operation, RDFPatch patch) { }

    List<CommitCreatedEvent> commits = new ArrayList<>();
    List<OperationResult> nonEmptyResults = new ArrayList<>();

    // Execute all operations against base commit and collect non-empty patches
    for (int i = 0; i < command.operations().size(); i++) {
      GraphOperation operation = command.operations().get(i);
      RDFPatch patch = executeOperation(
          operation,
          command.dataset(),
          command.baseCommit()
      );

      if (!graphDiffService.isPatchEmpty(patch)) {
        nonEmptyResults.add(new OperationResult(i, operation, patch));
      }
    }

    // Return null if all operations were no-ops
    if (nonEmptyResults.isEmpty()) {
      return null;
    }

    // Check for conflicts once with combined patch
    List<RDFPatch> allPatches = nonEmptyResults.stream()
        .map(OperationResult::patch)
        .toList();
    RDFPatch combinedPatch = combinePatches(allPatches);
    conflictDetectionService.checkForConcurrentWrites(
        command.dataset(),
        branch.getCommitId(),
        command.baseCommit(),
        combinedPatch
    );

    // Create chain of commits (each builds on previous)
    CommitId currentCommit = command.baseCommit();
    for (OperationResult result : nonEmptyResults) {
      CommitId commitId = CommitId.generate();
      String patchString = serializePatch(result.patch());

      String operationMessage = command.message() + " [Operation "
          + (result.operationIndex() + 1) + ": "
          + formatOperationDescription(result.operation()) + "]";

      CommitCreatedEvent commitEvent = new CommitCreatedEvent(
          command.dataset(),
          commitId.value(),
          List.of(currentCommit.value()),
          command.branch(),
          operationMessage,
          command.author(),
          Instant.now(),
          patchString
      );

      commits.add(commitEvent);
      currentCommit = commitId;
    }

    return new BatchGraphsCompletedEvent(
        command.dataset(),
        commits,
        Instant.now()
    );
  }

  private RDFPatch executeOperation(
      GraphOperation operation,
      String dataset,
      CommitId baseCommit) {

    String graphIri = operation.graphIri();
    boolean isDefaultGraph = operation.isDefaultGraph();

    switch (operation.method()) {
      case "PUT":
        return executePut(operation, dataset, baseCommit, graphIri, isDefaultGraph);
      case "POST":
        return executePost(operation, dataset, baseCommit, graphIri, isDefaultGraph);
      case "PATCH":
        return executePatch(operation, dataset, baseCommit, graphIri, isDefaultGraph);
      case "DELETE":
        return executeDelete(operation, dataset, baseCommit, graphIri, isDefaultGraph);
      default:
        throw new IllegalArgumentException("Unsupported method: " + operation.method());
    }
  }

  private RDFPatch executePut(
      GraphOperation operation,
      String dataset,
      CommitId baseCommit,
      String graphIri,
      boolean isDefaultGraph) {

    // Get current graph
    Model oldGraph = GraphCommandUtil.getCurrentGraph(
        datasetService,
        dataset,
        baseCommit,
        graphIri,
        isDefaultGraph
    );

    // Parse new content
    Model newGraph = rdfParsingService.parseRdf(
        operation.rdfContent(),
        operation.contentType()
    );

    // Compute diff
    return graphDiffService.computePutDiff(
        oldGraph,
        newGraph,
        isDefaultGraph ? null : graphIri
    );
  }

  private RDFPatch executePost(
      GraphOperation operation,
      String dataset,
      CommitId baseCommit,
      String graphIri,
      boolean isDefaultGraph) {

    // Get current graph
    Model oldGraph = GraphCommandUtil.getCurrentGraph(
        datasetService,
        dataset,
        baseCommit,
        graphIri,
        isDefaultGraph
    );

    // Parse new content
    Model newTriples = rdfParsingService.parseRdf(
        operation.rdfContent(),
        operation.contentType()
    );

    // Compute diff (ADD only)
    return graphDiffService.computePostDiff(
        oldGraph,
        newTriples,
        isDefaultGraph ? null : graphIri
    );
  }

  @SuppressWarnings("PMD.UnusedFormalParameter")
  private RDFPatch executePatch(
      GraphOperation operation,
      String dataset,
      CommitId baseCommit,
      String graphIri,
      boolean isDefaultGraph) {

    // Parse patch
    RDFPatch clientPatch = rdfPatchService.parsePatch(operation.patch());

    // Filter patch to target graph only
    return rdfPatchService.filterByGraph(clientPatch, isDefaultGraph ? null : graphIri);
  }

  @SuppressWarnings("PMD.UnusedFormalParameter")
  private RDFPatch executeDelete(
      GraphOperation operation,
      String dataset,
      CommitId baseCommit,
      String graphIri,
      boolean isDefaultGraph) {

    // Get current graph
    Model oldGraph = GraphCommandUtil.getCurrentGraph(
        datasetService,
        dataset,
        baseCommit,
        graphIri,
        isDefaultGraph
    );

    // Compute diff (DELETE all)
    Model emptyGraph = ModelFactory.createDefaultModel();
    return graphDiffService.computePutDiff(
        oldGraph,
        emptyGraph,
        isDefaultGraph ? null : graphIri
    );
  }

  private String formatOperationDescription(GraphOperation operation) {
    String graphDesc = operation.isDefaultGraph() ? "default graph"
        : "graph " + operation.graphIri();
    return operation.method() + " " + graphDesc;
  }

  private String serializePatch(RDFPatch patch) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    RDFPatchOps.write(outputStream, patch);
    return outputStream.toString(java.nio.charset.StandardCharsets.UTF_8);
  }

  /**
   * Combines multiple RDF Patches into a single patch.
   * All operations from all patches are merged in order.
   *
   * @param patches the list of patches to combine
   * @return the combined patch
   */
  private RDFPatch combinePatches(List<RDFPatch> patches) {
    if (patches.isEmpty()) {
      return RDFPatchOps.emptyPatch();
    }
    if (patches.size() == 1) {
      return patches.get(0);
    }

    RDFChangesCollector collector = new RDFChangesCollector();
    collector.txnBegin();

    // Apply all patches to the collector to merge operations
    for (RDFPatch patch : patches) {
      patch.apply(collector);
    }

    collector.txnCommit();
    return collector.getRDFPatch();
  }
}
