package org.chucc.vcserver.util;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.service.ConflictDetectionService;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.GraphDiffService;
import org.slf4j.Logger;

/**
 * Utility methods for graph command handlers.
 */
public final class GraphCommandUtil {

  private GraphCommandUtil() {
    // Utility class
  }

  /**
   * Gets the current graph state from the dataset.
   * Returns an empty model if the graph doesn't exist (for new graph creation).
   *
   * @param datasetService the dataset service
   * @param dataset the dataset name
   * @param baseCommit the base commit ID
   * @param graphIri the graph IRI (null for default graph)
   * @param isDefaultGraph true if this is the default graph
   * @return the current graph model, or empty model if graph doesn't exist
   */
  public static Model getCurrentGraph(
      DatasetService datasetService,
      String dataset,
      CommitId baseCommit,
      String graphIri,
      boolean isDefaultGraph) {
    if (isDefaultGraph) {
      return datasetService.getDefaultGraph(dataset, baseCommit);
    } else {
      Model graph = datasetService.getGraph(dataset, baseCommit, graphIri);
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
  public static String serializePatch(RDFPatch patch) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    RDFPatchOps.write(outputStream, patch);
    return outputStream.toString(java.nio.charset.StandardCharsets.UTF_8);
  }

  /**
   * Finalizes a graph command by checking for conflicts and creating a commit event.
   * Returns null if the patch is empty (no-op).
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param patch the RDF patch
   * @param graphDiffService the graph diff service
   * @param conflictDetectionService the conflict detection service
   * @param currentHead the current branch head commit ID
   * @param baseCommit the base commit ID
   * @param message the commit message
   * @param author the commit author
   * @return the commit created event, or null if no-op
   */
  public static VersionControlEvent finalizeGraphCommand(
      String dataset,
      String branch,
      RDFPatch patch,
      GraphDiffService graphDiffService,
      ConflictDetectionService conflictDetectionService,
      CommitId currentHead,
      CommitId baseCommit,
      String message,
      String author) {

    // Check for no-op (per SPARQL 1.2 Protocol: empty patch MUST NOT create commit)
    if (graphDiffService.isPatchEmpty(patch)) {
      return null; // Indicates no-op
    }

    // Check for concurrent writes
    conflictDetectionService.checkForConcurrentWrites(dataset, currentHead,
        baseCommit, patch);

    // Generate commit ID
    CommitId commitId = CommitId.generate();

    // Serialize patch to string
    String patchString = serializePatch(patch);

    // Produce event
    return new CommitCreatedEvent(
        dataset,
        commitId.value(),
        List.of(baseCommit.value()),
        branch,
        message,
        author,
        Instant.now(),
        patchString
    );
  }

  /**
   * Finalizes a graph command and publishes the resulting event to Kafka.
   * Combines finalization (conflict checking, event creation) with event publishing.
   * Returns null if the patch is empty (no-op).
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param patch the RDF patch
   * @param graphDiffService the graph diff service
   * @param conflictDetectionService the conflict detection service
   * @param currentHead the current branch head commit ID
   * @param baseCommit the base commit ID
   * @param message the commit message
   * @param author the commit author
   * @param eventPublisher the event publisher
   * @param logger the logger for error logging
   * @return the commit created event, or null if no-op
   */
  @SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
  public static VersionControlEvent finalizeAndPublishGraphCommand(
      String dataset,
      String branch,
      RDFPatch patch,
      GraphDiffService graphDiffService,
      ConflictDetectionService conflictDetectionService,
      CommitId currentHead,
      CommitId baseCommit,
      String message,
      String author,
      EventPublisher eventPublisher,
      Logger logger) {

    // Finalize command (check for no-op, conflicts, and create commit event)
    VersionControlEvent event = finalizeGraphCommand(
        dataset,
        branch,
        patch,
        graphDiffService,
        conflictDetectionService,
        currentHead,
        baseCommit,
        message,
        author
    );

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
}
