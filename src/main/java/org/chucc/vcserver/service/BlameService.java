package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.dto.BlameResponse;
import org.chucc.vcserver.dto.PaginationInfo;
import org.chucc.vcserver.dto.QuadBlameInfo;
import org.chucc.vcserver.exception.CommitNotFoundException;
import org.chucc.vcserver.exception.GraphNotFoundException;
import org.chucc.vcserver.repository.CommitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for computing blame information for RDF graphs.
 * Identifies the commit and author who last added each quad to a graph.
 */
@Service
public class BlameService {

  private static final Logger logger = LoggerFactory.getLogger(BlameService.class);
  private static final int MAX_LIMIT = 1000;

  private final DatasetService datasetService;
  private final CommitRepository commitRepository;

  /**
   * Constructor for BlameService.
   *
   * @param datasetService the dataset service for materializing commits
   * @param commitRepository the commit repository for loading commit history
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Services and repositories are Spring-managed beans"
  )
  public BlameService(DatasetService datasetService, CommitRepository commitRepository) {
    this.datasetService = datasetService;
    this.commitRepository = commitRepository;
  }

  /**
   * Computes blame for all quads in a specific graph.
   * Returns paginated results with attribution information.
   *
   * @param dataset the dataset name
   * @param targetCommitId the commit ID to blame
   * @param graphIri the graph IRI (use "urn:x-arq:DefaultGraph" for default graph)
   * @param offset number of results to skip
   * @param limit maximum results per page
   * @return blame response with quad attribution and pagination
   * @throws CommitNotFoundException if commit is not found
   * @throws GraphNotFoundException if graph is not found in commit
   * @throws IllegalArgumentException if limit exceeds maximum (1000)
   */
  public BlameResponse blameGraph(
      String dataset,
      CommitId targetCommitId,
      String graphIri,
      int offset,
      int limit
  ) {
    // Validate pagination parameters
    if (limit > MAX_LIMIT) {
      throw new IllegalArgumentException("Limit cannot exceed " + MAX_LIMIT);
    }

    // Load target commit
    final Commit targetCommit = commitRepository.findByDatasetAndId(dataset, targetCommitId)
        .orElseThrow(() -> new CommitNotFoundException(
            "Commit not found: " + targetCommitId.value(), true));

    // Materialize commit
    DatasetGraph targetDataset = datasetService.materializeCommit(dataset, targetCommitId);

    // Parse graph IRI to Node
    Node graphNode = parseGraphIri(graphIri);

    // Extract quads from target graph only
    Set<Quad> targetQuads = new HashSet<>();

    // Query quads directly from dataset for the specific graph
    targetDataset.find(graphNode, null, null, null)
        .forEachRemaining(targetQuads::add);

    // Validate graph has content
    if (targetQuads.isEmpty()) {
      throw new GraphNotFoundException(
          "Graph not found in commit: " + formatGraphIri(graphNode));
    }

    logger.debug("Blaming {} quads in graph {} at commit {}",
        targetQuads.size(), graphIri, targetCommitId.value());

    // Walk history backwards to find authors
    Map<Quad, QuadBlameInfo> blameMap = new HashMap<>();
    findAuthors(dataset, targetCommit, targetQuads, blameMap);

    // Convert to list and sort for deterministic pagination
    List<QuadBlameInfo> allQuads = new ArrayList<>(blameMap.values());
    allQuads.sort(createQuadComparator());

    // Apply pagination
    int totalQuads = allQuads.size();
    int endIndex = Math.min(offset + limit, totalQuads);
    List<QuadBlameInfo> paginatedQuads = allQuads.subList(offset, endIndex);

    // Build pagination info
    boolean hasMore = endIndex < totalQuads;
    PaginationInfo pagination = new PaginationInfo(limit, offset, hasMore);

    return new BlameResponse(
        targetCommitId.value(),
        formatGraphIri(graphNode),
        paginatedQuads,
        pagination
    );
  }

  /**
   * Walks commit history backwards using BFS to find the author of each quad.
   * Removes quads from tracking set as they are found (handles delete/re-add).
   *
   * @param dataset the dataset name
   * @param startCommit the commit to start from
   * @param targetQuads the quads to find authors for (modified in-place)
   * @param blameMap the map to populate with blame info
   */
  private void findAuthors(
      String dataset,
      Commit startCommit,
      Set<Quad> targetQuads,
      Map<Quad, QuadBlameInfo> blameMap
  ) {
    // Use BFS to traverse history (handles merge commits correctly)
    Queue<Commit> queue = new LinkedList<>();
    Set<CommitId> visited = new HashSet<>();

    queue.add(startCommit);
    visited.add(startCommit.id());

    while (!queue.isEmpty() && !targetQuads.isEmpty()) {
      Commit currentCommit = queue.poll();

      // Load patch for this commit
      Optional<RDFPatch> patchOpt = commitRepository.findPatchByDatasetAndId(
          dataset, currentCommit.id());

      if (patchOpt.isPresent()) {
        // Check if this commit added any of our target quads
        processCommitForBlame(currentCommit, patchOpt.get(), targetQuads, blameMap);
      }

      // Early exit if all quads found
      if (targetQuads.isEmpty()) {
        logger.debug("All quads found, early exit at commit {}", currentCommit.id().value());
        break;
      }

      // Add parents to queue
      for (CommitId parentId : currentCommit.parents()) {
        if (!visited.contains(parentId)) {
          visited.add(parentId);
          commitRepository.findByDatasetAndId(dataset, parentId)
              .ifPresent(queue::add);
        }
      }
    }

    // Log warning if any quads remain unfound
    if (!targetQuads.isEmpty()) {
      logger.warn("Blame: {} quads not found in history for dataset {}",
          targetQuads.size(), dataset);
    }
  }

  /**
   * Checks if a commit added any target quads (via ADD operations).
   * If found, records blame info and removes quad from target set.
   * CRITICAL: Removal ensures delete/re-add scenarios are handled correctly.
   *
   * @param commit the commit to check
   * @param patch the RDF patch for this commit
   * @param targetQuads the quads we're looking for (modified in-place)
   * @param blameMap the map to populate with blame info
   */
  private void processCommitForBlame(
      Commit commit,
      RDFPatch patch,
      Set<Quad> targetQuads,
      Map<Quad, QuadBlameInfo> blameMap
  ) {
    // Use RDFChanges to inspect ADD operations only
    patch.apply(new org.chucc.vcserver.util.RdfChangesAdapter() {
      @Override
      public void add(Node g, Node s, Node p, Node o) {
        final var graph =
            // both is valid for default graph in parsers
            g == null || g.equals(Quad.defaultGraphNodeGenerated)
            // this is Jena's standard for default graph in a dataset
            ? Quad.defaultGraphIRI
            : g;
        Quad quad = Quad.create(graph, s, p, o);

        if (targetQuads.contains(quad)) {
          // Found the birth of this quad!
          QuadBlameInfo blameInfo = new QuadBlameInfo(
              formatNode(s),
              formatNode(p),
              formatNode(o),
              commit.author(),
              commit.timestamp(),
              commit.id().value()
          );

          blameMap.put(quad, blameInfo);

          // CRITICAL: Remove from target set
          // This ensures we don't blame an older ADD if quad was deleted and re-added
          targetQuads.remove(quad);
        }
      }

      @Override
      public void delete(Node g, Node s, Node p, Node o) {
        // Ignore deletions - we only care about additions
      }
    });
  }

  /**
   * Parses a graph IRI string to a Jena Node.
   * Requires canonical URI (urn:x-arq:DefaultGraph for default graph).
   *
   * @param graphIri the graph IRI (must use canonical URIs)
   * @return the graph Node
   */
  private Node parseGraphIri(String graphIri) {
    // No special keyword handling - users must provide canonical URIs
    // This prevents namespace collision: users can create graphs named "default"
    return NodeFactory.createURI(graphIri);
  }

  /**
   * Formats a graph node for display.
   * Returns the canonical URI representation.
   * For default graph, returns Jena's canonical URI to avoid ambiguity.
   *
   * @param graphNode the graph node
   * @return the formatted graph IRI (canonical URI for default graph)
   */
  private String formatGraphIri(Node graphNode) {
    if (graphNode == null
        || graphNode.equals(Quad.defaultGraphNodeGenerated)
        || graphNode.equals(Quad.defaultGraphIRI)) {
      // Return canonical Jena default graph URI to avoid ambiguity with
      // user graphs that might be named "default"
      return Quad.defaultGraphIRI.getURI();
    }
    return graphNode.getURI();
  }

  /**
   * Formats a node for display in blame response.
   * Handles URIs, literals, and blank nodes.
   *
   * @param node the node to format
   * @return the formatted node string
   */
  private String formatNode(Node node) {
    if (node.isURI()) {
      return node.getURI();
    } else if (node.isLiteral()) {
      // Includes quotes and datatype/language tag
      return node.toString();
    } else if (node.isBlank()) {
      // Note: Blank node labels may not be stable across serialization
      return "_:" + node.getBlankNodeLabel();
    } else {
      return node.toString();
    }
  }

  /**
   * Creates a comparator for stable quad sorting.
   * Sorting order: subject, predicate, object (all lexicographic).
   *
   * @return the comparator for QuadBlameInfo
   */
  private Comparator<QuadBlameInfo> createQuadComparator() {
    return Comparator.comparing(QuadBlameInfo::subject)
        .thenComparing(QuadBlameInfo::predicate)
        .thenComparing(QuadBlameInfo::object);
  }
}
