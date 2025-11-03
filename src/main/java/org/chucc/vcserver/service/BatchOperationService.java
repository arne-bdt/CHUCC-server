package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.chucc.vcserver.dto.WriteOperation;
import org.chucc.vcserver.exception.MalformedUpdateException;
import org.chucc.vcserver.util.RdfPatchUtil;
import org.springframework.stereotype.Service;

/**
 * Service for batch write operations.
 *
 * <p>Combines multiple write operations (SPARQL updates or RDF patches) into a single commit.
 * Each operation is converted to an RDF patch, then all patches are combined into one.
 *
 * <p>This service orchestrates the batch operation without creating events - the controller
 * is responsible for event publication via CreateCommitCommandHandler.
 */
@Service
public class BatchOperationService {

  private final DatasetService datasetService;
  private final RdfPatchService rdfPatchService;

  /**
   * Constructs a new BatchOperationService.
   *
   * @param datasetService the dataset service for materializing branch states
   * @param rdfPatchService the RDF patch service for parsing patches
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Services are Spring-managed beans and are intentionally shared")
  public BatchOperationService(
      DatasetService datasetService,
      RdfPatchService rdfPatchService) {
    this.datasetService = datasetService;
    this.rdfPatchService = rdfPatchService;
  }

  /**
   * Converts all operations to RDF patches and combines them into a single patch.
   *
   * <p>For SPARQL updates, materializes the current branch state, executes the update
   * on a clone, and computes the diff. For direct patches, parses them directly.
   *
   * @param dataset Dataset name
   * @param operations List of write operations to combine
   * @param branch Target branch name
   * @return Combined RDF patch containing all operations
   * @throws IllegalArgumentException if any operation is invalid or fails
   */
  public RDFPatch combineOperations(
      String dataset,
      List<WriteOperation> operations,
      String branch) {

    List<RDFPatch> patches = new ArrayList<>();

    for (int i = 0; i < operations.size(); i++) {
      WriteOperation op = operations.get(i);
      try {
        RDFPatch patch = convertToPatch(dataset, op, branch);
        patches.add(patch);
      } catch (Exception e) {
        throw new IllegalArgumentException(
            "Failed to process operation " + i + ": " + e.getMessage(), e);
      }
    }

    return combine(patches);
  }

  /**
   * Converts a single write operation to an RDF patch.
   *
   * @param dataset Dataset name
   * @param op Write operation to convert
   * @param branch Target branch name
   * @return RDF patch representing the operation
   * @throws IllegalArgumentException if operation type is unknown
   */
  private RDFPatch convertToPatch(
      String dataset,
      WriteOperation op,
      String branch) {

    return switch (op.type()) {
      case "update" -> convertUpdateToPatch(dataset, op.sparql(), branch);
      case "applyPatch" -> rdfPatchService.parsePatch(op.patch());
      default -> throw new IllegalArgumentException("Unknown operation type: " + op.type());
    };
  }

  /**
   * Converts a SPARQL update to an RDF patch by executing it and computing the diff.
   *
   * <p>This mirrors the logic in SparqlUpdateCommandHandler:
   * <ol>
   *   <li>Materialize current branch HEAD</li>
   *   <li>Clone the dataset</li>
   *   <li>Execute SPARQL update on the clone</li>
   *   <li>Compute diff between original and modified</li>
   * </ol>
   *
   * @param dataset Dataset name
   * @param sparql SPARQL update query
   * @param branch Target branch name
   * @return RDF patch representing the changes
   * @throws MalformedUpdateException if SPARQL syntax is invalid
   */
  private RDFPatch convertUpdateToPatch(String dataset, String sparql, String branch) {
    // Materialize current branch state
    org.chucc.vcserver.domain.DatasetRef datasetRef =
        new org.chucc.vcserver.domain.DatasetRef(dataset, branch);
    DatasetGraph currentDataset = datasetService.getMutableDataset(datasetRef).asDatasetGraph();

    // Clone for modification
    DatasetGraph modifiedDataset = cloneDataset(currentDataset);

    // Execute SPARQL update
    try {
      UpdateRequest updateRequest = UpdateFactory.create(sparql);
      UpdateAction.execute(updateRequest, modifiedDataset);
    } catch (org.apache.jena.query.QueryParseException e) {
      throw new MalformedUpdateException(
          "SPARQL UPDATE query is malformed: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Failed to execute SPARQL UPDATE: " + e.getMessage(), e);
    }

    // Compute diff
    return RdfPatchUtil.diff(currentDataset, modifiedDataset);
  }

  /**
   * Clones a DatasetGraph for safe modification.
   *
   * @param dataset The dataset to clone
   * @return A deep copy of the dataset
   */
  private DatasetGraph cloneDataset(DatasetGraph dataset) {
    DatasetGraph clone = new org.apache.jena.sparql.core.mem.DatasetGraphInMemory();
    dataset.find().forEachRemaining(clone::add);
    return clone;
  }

  /**
   * Combines multiple RDF patches into a single patch.
   *
   * <p>All operations from all patches are collected into a single patch in order.
   * This is equivalent to applying each patch sequentially.
   *
   * @param patches List of patches to combine
   * @return Single patch containing all operations
   */
  private RDFPatch combine(List<RDFPatch> patches) {
    RDFChangesCollector collector = new RDFChangesCollector();

    // Start transaction
    collector.txnBegin();

    // Apply each patch to the collector (this copies all operations)
    for (RDFPatch patch : patches) {
      patch.apply(collector);
    }

    // Commit transaction
    collector.txnCommit();

    return collector.getRDFPatch();
  }
}
