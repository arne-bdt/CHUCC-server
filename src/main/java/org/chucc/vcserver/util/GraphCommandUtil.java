package org.chucc.vcserver.util;

import java.io.ByteArrayOutputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.service.DatasetService;

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
}
