package org.chucc.vcserver.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.apache.jena.riot.RiotException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service for parsing, filtering, and applying RDF Patches.
 * Supports PATCH operations on graphs with patch validation.
 */
@Service
public class RdfPatchService {

  /**
   * Parses an RDF Patch from text/rdf-patch format.
   *
   * @param patchText the patch text to parse
   * @return the parsed RDFPatch
   * @throws ResponseStatusException with 400 status if syntax is invalid
   */
  public RDFPatch parsePatch(String patchText) {
    if (patchText == null || patchText.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Patch content cannot be empty"
      );
    }

    try {
      InputStream inputStream = new ByteArrayInputStream(
          patchText.getBytes(StandardCharsets.UTF_8)
      );
      return RDFPatchOps.read(inputStream);
    } catch (RiotException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Invalid RDF Patch syntax: " + e.getMessage(),
          e
      );
    }
  }

  /**
   * Filters an RDF Patch to include only operations on the target graph.
   *
   * @param patch the patch to filter
   * @param graphIri the target graph IRI (null for default graph)
   * @return filtered patch containing only operations for the target graph
   */
  public RDFPatch filterByGraph(RDFPatch patch, String graphIri) {
    RDFChangesCollector collector = new RDFChangesCollector();

    // Apply the patch to the collector with filtering
    patch.apply(new org.apache.jena.rdfpatch.changes.RDFChangesWrapper(collector) {
      private boolean graphMatches(Node g) {
        // Check if g represents the default graph
        boolean isDefaultGraphNode = g == null
            || g.equals(org.apache.jena.sparql.core.Quad.defaultGraphNodeGenerated)
            || g.equals(org.apache.jena.sparql.core.Quad.defaultGraphIRI);

        if (graphIri == null) {
          // Patching default graph - match default graph nodes
          return isDefaultGraphNode;
        } else {
          // Patching named graph - match either:
          // 1. Explicit named graph nodes that match the graphIri
          // 2. Default graph nodes (interpreted as targeting the current named graph)
          return isDefaultGraphNode
              || (g.isURI() && g.getURI().equals(graphIri));
        }
      }

      @Override
      public void add(Node g, Node s, Node p, Node o) {
        if (graphMatches(g)) {
          super.add(g, s, p, o);
        }
      }

      @Override
      public void delete(Node g, Node s, Node p, Node o) {
        if (graphMatches(g)) {
          super.delete(g, s, p, o);
        }
      }
    });

    return collector.getRDFPatch();
  }

  /**
   * Checks if a patch can be applied to a graph.
   * Validates that DELETE operations target existing triples.
   *
   * @param graph the graph to apply the patch to
   * @param patch the patch to validate
   * @return true if the patch can be applied, false otherwise
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "REC_CATCH_EXCEPTION",
      justification = "Need to catch all exceptions to determine if patch is applicable")
  public boolean canApply(Model graph, RDFPatch patch) {
    try {
      // Create a copy to test application
      Model testGraph = ModelFactory.createDefaultModel();
      testGraph.add(graph);

      // Create a validating wrapper that checks DELETE operations
      class ValidatingChanges extends org.apache.jena.rdfpatch.changes.RDFChangesWrapper {
        private boolean valid = true;

        ValidatingChanges(org.apache.jena.rdfpatch.RDFChanges wrapped) {
          super(wrapped);
        }

        @Override
        public void delete(Node g, Node s, Node p, Node o) {
          // Check if the triple exists in the graph before deleting
          org.apache.jena.rdf.model.Statement stmt = testGraph.asStatement(
              org.apache.jena.graph.Triple.create(s, p, o)
          );
          if (!testGraph.contains(stmt)) {
            valid = false;
          } else {
            super.delete(g, s, p, o);
          }
        }

        public boolean isValid() {
          return valid;
        }
      }

      // Create a dataset graph for patch application
      org.apache.jena.sparql.core.DatasetGraph dsg =
          org.apache.jena.sparql.core.DatasetGraphFactory.wrap(testGraph.getGraph());

      ValidatingChanges validating = new ValidatingChanges(
          new org.apache.jena.rdfpatch.changes.RDFChangesApply(dsg)
      );

      // Apply the patch with validation
      patch.apply(validating);

      return validating.isValid();
    } catch (Exception e) {
      // If any operation fails, return false
      return false;
    }
  }

  /**
   * Applies an RDF Patch to a graph and returns the modified graph.
   *
   * @param graph the graph to apply the patch to
   * @param patch the patch to apply
   * @return the modified graph (a new model instance)
   */
  public Model applyPatch(Model graph, RDFPatch patch) {
    // Create a copy of the graph
    Model result = ModelFactory.createDefaultModel();
    result.add(graph);

    // Create a dataset graph for patch application
    org.apache.jena.sparql.core.DatasetGraph dsg =
        org.apache.jena.sparql.core.DatasetGraphFactory.wrap(result.getGraph());

    // Apply the patch
    patch.apply(new org.apache.jena.rdfpatch.changes.RDFChangesApply(dsg));

    return result;
  }

}
