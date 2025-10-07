package org.chucc.vcserver.controller;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.dto.ProblemDetail;
import org.chucc.vcserver.util.GraphParameterValidator;
import org.chucc.vcserver.util.SelectorValidator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SPARQL 1.2 Graph Store Protocol endpoint with version control support.
 * Implements graph-level operations per SPARQL 1.2 GSP specification.
 */
@RestController
@RequestMapping("/data")
@Tag(name = "Graph Store Protocol",
    description = "SPARQL 1.2 Graph Store Protocol operations with version control")
public class GraphStoreController {

  private static final String DATASET_NAME = "default";

  @SuppressFBWarnings(
      value = "URF_UNREAD_FIELD",
      justification = "Field reserved for future implementation of write operations")
  private final VersionControlProperties vcProperties;
  private final org.chucc.vcserver.service.DatasetService datasetService;
  private final org.chucc.vcserver.service.GraphSerializationService serializationService;
  private final org.chucc.vcserver.service.SelectorResolutionService selectorResolutionService;

  /**
   * Constructor for GraphStoreController.
   *
   * @param vcProperties version control configuration properties
   * @param datasetService service for dataset operations
   * @param serializationService service for RDF serialization
   * @param selectorResolutionService service for resolving version selectors
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring-managed beans are intentionally shared references")
  public GraphStoreController(
      VersionControlProperties vcProperties,
      org.chucc.vcserver.service.DatasetService datasetService,
      org.chucc.vcserver.service.GraphSerializationService serializationService,
      org.chucc.vcserver.service.SelectorResolutionService selectorResolutionService) {
    this.vcProperties = vcProperties;
    this.datasetService = datasetService;
    this.serializationService = serializationService;
    this.selectorResolutionService = selectorResolutionService;
  }

  /**
   * OPTIONS method for capability discovery.
   * Returns headers advertising supported methods and version control features.
   *
   * @return response with capability headers
   */
  @RequestMapping(method = RequestMethod.OPTIONS)
  @Operation(
      summary = "Discover Graph Store Protocol capabilities",
      description = "Returns headers advertising supported HTTP methods, "
          + "RDF Patch support, and version control capabilities."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Capabilities advertised via headers",
      headers = {
          @Header(
              name = "Allow",
              description = "Supported HTTP methods",
              schema = @Schema(type = "string",
                  example = "GET, PUT, POST, DELETE, HEAD, PATCH, OPTIONS")
          ),
          @Header(
              name = "Accept-Patch",
              description = "Supported patch formats",
              schema = @Schema(type = "string", example = "text/rdf-patch")
          ),
          @Header(
              name = "SPARQL-Version-Control",
              description = "Version control protocol version",
              schema = @Schema(type = "string", example = "1.0")
          ),
          @Header(
              name = "Link",
              description = "Link to version control endpoint",
              schema = @Schema(type = "string", example = "</version>; rel=\"version-control\"")
          )
      }
  )
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<Void> options() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.ALLOW, "GET, PUT, POST, DELETE, HEAD, PATCH, OPTIONS");
    headers.set(HttpHeaders.ACCEPT_PATCH, "text/rdf-patch");
    headers.set("SPARQL-Version-Control", "1.0");
    headers.set(HttpHeaders.LINK, "</version>; rel=\"version-control\"");

    return ResponseEntity.ok().headers(headers).build();
  }

  /**
   * GET method to retrieve a graph.
   *
   * @param graph the graph IRI parameter
   * @param isDefault the default flag parameter
   * @param branch target branch
   * @param commit target commit
   * @param asOf timestamp for time-travel query
   * @param accept requested RDF format
   * @return response with graph content
   */
  // CPD-OFF - Method signature follows GSP spec, duplication is intentional
  @GetMapping(produces = {
      "text/turtle",
      "application/rdf+xml",
      "application/n-triples",
      "application/ld+json",
      "text/n3"
  })
  @Operation(
      summary = "GET graph",
      description = "Retrieve RDF graph content. Use ?graph=IRI for named graphs or "
          + "?default=true for the default graph. "
          + "Version control: branch, commit, or asOf parameters select the dataset state."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Graph content returned",
      headers = @Header(
          name = "ETag",
          description = "Commit ID of the graph state",
          schema = @Schema(type = "string")
      )
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request - Invalid parameters",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Not Found - Graph does not exist",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "501",
      description = "Not Implemented",
      content = @Content(mediaType = "application/problem+json")
  )
  @SuppressWarnings({"PMD.UseObjectForClearerAPI", "PMD.LooseCoupling"})
  // Method signature matches GSP spec; HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<String> getGraph(
      @Parameter(description = "Named graph IRI")
      @RequestParam(required = false) String graph,
      @Parameter(description = "Default graph flag")
      @RequestParam(name = "default", required = false) Boolean isDefault,
      @Parameter(description = "Target branch")
      @RequestParam(required = false) String branch,
      @Parameter(description = "Target commit (read-only)")
      @RequestParam(required = false) String commit,
      @Parameter(description = "Query state at or before this timestamp (ISO8601)")
      @RequestParam(required = false) String asOf,
      @Parameter(description = "Requested RDF format")
      @RequestHeader(name = HttpHeaders.ACCEPT, required = false,
          defaultValue = "text/turtle") String accept) {

    // Validate parameters and resolve selector
    org.chucc.vcserver.domain.CommitId commitId =
        resolveGraphSelector(graph, isDefault, branch, commit, asOf);

    // Get the graph from the dataset (throws GraphNotFoundException if not found)
    org.apache.jena.rdf.model.Model model = getModelFromDataset(graph, isDefault, commitId);

    // Build response headers
    HttpHeaders headers = buildResponseHeaders(
        commitId,
        serializationService.getContentType(
            org.apache.jena.riot.RDFLanguages.contentTypeToLang(accept))
    );

    // Serialize the graph
    String serialized = serializationService.serializeGraph(model, accept);

    return ResponseEntity.ok().headers(headers).body(serialized);
  }
  // CPD-ON

  /**
   * HEAD method to retrieve graph metadata without content.
   *
   * @param graph the graph IRI parameter
   * @param isDefault the default flag parameter
   * @param branch target branch
   * @param commit target commit
   * @param asOf timestamp for time-travel query
   * @return response with headers but no body
   */
  // CPD-OFF - Method signature follows GSP spec, duplication is intentional
  @RequestMapping(method = RequestMethod.HEAD)
  @Operation(
      summary = "HEAD graph",
      description = "Check graph existence and retrieve metadata without content. "
          + "Returns same headers as GET but no body."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Graph exists",
      headers = @Header(
          name = "ETag",
          description = "Commit ID of the graph state",
          schema = @Schema(type = "string")
      )
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request - Invalid parameters"
  )
  @ApiResponse(
      responseCode = "404",
      description = "Not Found - Graph does not exist"
  )
  @ApiResponse(
      responseCode = "501",
      description = "Not Implemented"
  )
  @SuppressWarnings({"PMD.UseObjectForClearerAPI", "PMD.LooseCoupling"})
  // Method signature matches GSP spec; HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<Void> headGraph(
      @Parameter(description = "Named graph IRI")
      @RequestParam(required = false) String graph,
      @Parameter(description = "Default graph flag")
      @RequestParam(name = "default", required = false) Boolean isDefault,
      @Parameter(description = "Target branch")
      @RequestParam(required = false) String branch,
      @Parameter(description = "Target commit (read-only)")
      @RequestParam(required = false) String commit,
      @Parameter(description = "Query state at or before this timestamp (ISO8601)")
      @RequestParam(required = false) String asOf) {

    // Validate parameters and resolve selector (reuse GET logic)
    org.chucc.vcserver.domain.CommitId commitId =
        resolveGraphSelector(graph, isDefault, branch, commit, asOf);

    // Check graph existence (throws GraphNotFoundException if not found)
    getModelFromDataset(graph, isDefault, commitId);

    // Build response headers (same as GET, but default to text/turtle for content-type)
    HttpHeaders headers = buildResponseHeaders(commitId, "text/turtle");

    // Return headers with no body
    return ResponseEntity.ok().headers(headers).build();
  }
  // CPD-ON

  /**
   * PUT method to create or replace a graph.
   *
   * @param graph the graph IRI parameter
   * @param isDefault the default flag parameter
   * @param branch target branch
   * @param commit target commit
   * @param asOf timestamp for time-travel query
   * @param author commit author from header
   * @param message commit message from header
   * @param ifMatch precondition header for optimistic locking
   * @param body RDF graph content
   * @return 501 Not Implemented (stub)
   */
  // CPD-OFF - Method signature follows GSP spec, duplication is intentional
  @PutMapping(consumes = {
      "text/turtle",
      "application/rdf+xml",
      "application/n-triples",
      "application/ld+json",
      "text/n3"
  })
  @Operation(
      summary = "PUT graph",
      description = "Create or replace graph content. Creates a new commit on the target branch."
  )
  @ApiResponse(
      responseCode = "201",
      description = "Graph created",
      headers = {
          @Header(
              name = "Location",
              description = "URI of the created graph",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "ETag",
              description = "Commit ID of the new state",
              schema = @Schema(type = "string")
          )
      }
  )
  @ApiResponse(
      responseCode = "204",
      description = "Graph replaced",
      headers = @Header(
          name = "ETag",
          description = "Commit ID of the new state",
          schema = @Schema(type = "string")
      )
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request - Invalid parameters or content",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "412",
      description = "Precondition Failed - If-Match header mismatch",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "501",
      description = "Not Implemented",
      content = @Content(mediaType = "application/problem+json")
  )
  @SuppressWarnings("PMD.UseObjectForClearerAPI") // Method signature matches GSP spec
  public ResponseEntity<ProblemDetail> putGraph(
      @Parameter(description = "Named graph IRI")
      @RequestParam(required = false) String graph,
      @Parameter(description = "Default graph flag")
      @RequestParam(name = "default", required = false) Boolean isDefault,
      @Parameter(description = "Target branch")
      @RequestParam(required = false) String branch,
      @Parameter(description = "Target commit (read-only)")
      @RequestParam(required = false) String commit,
      @Parameter(description = "Query state at or before this timestamp (ISO8601)")
      @RequestParam(required = false) String asOf,
      @Parameter(description = "Commit author")
      @RequestHeader(name = "SPARQL-VC-Author", required = false) String author,
      @Parameter(description = "Commit message")
      @RequestHeader(name = "SPARQL-VC-Message", required = false) String message,
      @Parameter(description = "Expected ETag for optimistic locking")
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @RequestBody String body) {

    validateParameters(graph, isDefault, branch, commit, asOf);
    return notImplemented("PUT");
  }
  // CPD-ON

  /**
   * POST method to merge data into a graph.
   *
   * @param graph the graph IRI parameter
   * @param isDefault the default flag parameter
   * @param branch target branch
   * @param commit target commit
   * @param asOf timestamp for time-travel query
   * @param author commit author from header
   * @param message commit message from header
   * @param ifMatch precondition header for optimistic locking
   * @param body RDF data to merge
   * @return 501 Not Implemented (stub)
   */
  // CPD-OFF - Method signature follows GSP spec, duplication is intentional
  @PostMapping(consumes = {
      "text/turtle",
      "application/rdf+xml",
      "application/n-triples",
      "application/ld+json",
      "text/n3"
  })
  @Operation(
      summary = "POST graph",
      description = "Merge RDF data into existing graph. Adds triples to the graph. "
          + "Creates a new commit on the target branch."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Data merged successfully",
      headers = @Header(
          name = "ETag",
          description = "Commit ID of the new state",
          schema = @Schema(type = "string")
      )
  )
  @ApiResponse(
      responseCode = "201",
      description = "Graph created with initial data",
      headers = {
          @Header(
              name = "Location",
              description = "URI of the created graph",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "ETag",
              description = "Commit ID of the new state",
              schema = @Schema(type = "string")
          )
      }
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request - Invalid parameters or content",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "412",
      description = "Precondition Failed - If-Match header mismatch",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "501",
      description = "Not Implemented",
      content = @Content(mediaType = "application/problem+json")
  )
  @SuppressWarnings("PMD.UseObjectForClearerAPI") // Method signature matches GSP spec
  public ResponseEntity<ProblemDetail> postGraph(
      @Parameter(description = "Named graph IRI")
      @RequestParam(required = false) String graph,
      @Parameter(description = "Default graph flag")
      @RequestParam(name = "default", required = false) Boolean isDefault,
      @Parameter(description = "Target branch")
      @RequestParam(required = false) String branch,
      @Parameter(description = "Target commit (read-only)")
      @RequestParam(required = false) String commit,
      @Parameter(description = "Query state at or before this timestamp (ISO8601)")
      @RequestParam(required = false) String asOf,
      @Parameter(description = "Commit author")
      @RequestHeader(name = "SPARQL-VC-Author", required = false) String author,
      @Parameter(description = "Commit message")
      @RequestHeader(name = "SPARQL-VC-Message", required = false) String message,
      @Parameter(description = "Expected ETag for optimistic locking")
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @RequestBody String body) {

    validateParameters(graph, isDefault, branch, commit, asOf);
    return notImplemented("POST");
  }
  // CPD-ON

  /**
   * DELETE method to remove a graph.
   *
   * @param graph the graph IRI parameter
   * @param isDefault the default flag parameter
   * @param branch target branch
   * @param commit target commit
   * @param asOf timestamp for time-travel query
   * @param author commit author from header
   * @param message commit message from header
   * @param ifMatch precondition header for optimistic locking
   * @return 501 Not Implemented (stub)
   */
  // CPD-OFF - Method signature follows GSP spec, duplication is intentional
  @DeleteMapping
  @Operation(
      summary = "DELETE graph",
      description = "Remove all triples from a graph. Creates a new commit on the target branch."
  )
  @ApiResponse(
      responseCode = "204",
      description = "Graph deleted successfully",
      headers = @Header(
          name = "ETag",
          description = "Commit ID of the new state",
          schema = @Schema(type = "string")
      )
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request - Invalid parameters",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Not Found - Graph does not exist",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "412",
      description = "Precondition Failed - If-Match header mismatch",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "501",
      description = "Not Implemented",
      content = @Content(mediaType = "application/problem+json")
  )
  @SuppressWarnings("PMD.UseObjectForClearerAPI") // Method signature matches GSP spec
  public ResponseEntity<ProblemDetail> deleteGraph(
      @Parameter(description = "Named graph IRI")
      @RequestParam(required = false) String graph,
      @Parameter(description = "Default graph flag")
      @RequestParam(name = "default", required = false) Boolean isDefault,
      @Parameter(description = "Target branch")
      @RequestParam(required = false) String branch,
      @Parameter(description = "Target commit (read-only)")
      @RequestParam(required = false) String commit,
      @Parameter(description = "Query state at or before this timestamp (ISO8601)")
      @RequestParam(required = false) String asOf,
      @Parameter(description = "Commit author")
      @RequestHeader(name = "SPARQL-VC-Author", required = false) String author,
      @Parameter(description = "Commit message")
      @RequestHeader(name = "SPARQL-VC-Message", required = false) String message,
      @Parameter(description = "Expected ETag for optimistic locking")
      @RequestHeader(name = "If-Match", required = false) String ifMatch) {

    validateParameters(graph, isDefault, branch, commit, asOf);
    return notImplemented("DELETE");
  }
  // CPD-ON

  /**
   * PATCH method to apply RDF Patch to a graph.
   *
   * @param graph the graph IRI parameter
   * @param isDefault the default flag parameter
   * @param branch target branch
   * @param commit target commit
   * @param asOf timestamp for time-travel query
   * @param author commit author from header
   * @param message commit message from header
   * @param ifMatch precondition header for optimistic locking
   * @param patchBody RDF Patch content
   * @return 501 Not Implemented (stub)
   */
  // CPD-OFF - Method signature follows GSP spec, duplication is intentional
  @PatchMapping(consumes = "text/rdf-patch")
  @Operation(
      summary = "PATCH graph",
      description = "Apply RDF Patch to a graph. Version control extension. "
          + "Creates a new commit on the target branch."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Patch applied successfully",
      headers = @Header(
          name = "ETag",
          description = "Commit ID of the new state",
          schema = @Schema(type = "string")
      )
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request - Invalid patch or parameters",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Not Found - Graph does not exist",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "409",
      description = "Conflict - Patch cannot be applied",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "412",
      description = "Precondition Failed - If-Match header mismatch",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "501",
      description = "Not Implemented",
      content = @Content(mediaType = "application/problem+json")
  )
  @SuppressWarnings("PMD.UseObjectForClearerAPI") // Method signature matches GSP spec
  public ResponseEntity<ProblemDetail> patchGraph(
      @Parameter(description = "Named graph IRI")
      @RequestParam(required = false) String graph,
      @Parameter(description = "Default graph flag")
      @RequestParam(name = "default", required = false) Boolean isDefault,
      @Parameter(description = "Target branch")
      @RequestParam(required = false) String branch,
      @Parameter(description = "Target commit (read-only)")
      @RequestParam(required = false) String commit,
      @Parameter(description = "Query state at or before this timestamp (ISO8601)")
      @RequestParam(required = false) String asOf,
      @Parameter(description = "Commit author")
      @RequestHeader(name = "SPARQL-VC-Author", required = false) String author,
      @Parameter(description = "Commit message")
      @RequestHeader(name = "SPARQL-VC-Message", required = false) String message,
      @Parameter(description = "Expected ETag for optimistic locking")
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @RequestBody String patchBody) {

    validateParameters(graph, isDefault, branch, commit, asOf);
    return notImplemented("PATCH");
  }
  // CPD-ON

  /**
   * Validates graph and selector parameters.
   *
   * @param graph the graph IRI parameter
   * @param isDefault the default flag parameter
   * @param branch the branch selector
   * @param commit the commit selector
   * @param asOf the asOf selector
   */
  private void validateParameters(String graph, Boolean isDefault, String branch,
      String commit, String asOf) {
    GraphParameterValidator.validateGraphParameter(graph, isDefault);
    SelectorValidator.validateMutualExclusion(branch, commit, asOf);
  }

  /**
   * Resolves graph selector to a CommitId.
   *
   * @param graph the graph IRI parameter
   * @param isDefault the default flag parameter
   * @param branch the branch selector
   * @param commit the commit selector
   * @param asOf the asOf selector
   * @return the resolved CommitId
   */
  private org.chucc.vcserver.domain.CommitId resolveGraphSelector(
      String graph, Boolean isDefault, String branch, String commit, String asOf) {
    validateParameters(graph, isDefault, branch, commit, asOf);
    return selectorResolutionService.resolve(DATASET_NAME, branch, commit, asOf);
  }

  /**
   * Retrieves model from dataset.
   *
   * @param graph the graph IRI parameter
   * @param isDefault the default flag parameter
   * @param commitId the resolved commit ID
   * @return the retrieved model
   * @throws org.chucc.vcserver.exception.GraphNotFoundException if named graph not found
   */
  private org.apache.jena.rdf.model.Model getModelFromDataset(
      String graph, Boolean isDefault, org.chucc.vcserver.domain.CommitId commitId) {
    if (isDefault != null && isDefault) {
      return datasetService.getDefaultGraph(DATASET_NAME, commitId);
    } else {
      org.apache.jena.rdf.model.Model model =
          datasetService.getGraph(DATASET_NAME, commitId, graph);
      if (model == null) {
        throw new org.chucc.vcserver.exception.GraphNotFoundException(graph);
      }
      return model;
    }
  }

  /**
   * Builds response headers for graph operations.
   *
   * @param commitId the commit ID to include in ETag
   * @param contentType the content type for the response
   * @return the built HttpHeaders
   */
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  private HttpHeaders buildResponseHeaders(
      org.chucc.vcserver.domain.CommitId commitId, String contentType) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(org.springframework.http.MediaType.parseMediaType(contentType));
    headers.setETag("\"" + commitId.value() + "\"");
    headers.set("SPARQL-Version-Control", "true");
    return headers;
  }

  /**
   * Creates a 501 Not Implemented response.
   *
   * @param operation the operation name
   * @return ResponseEntity with 501 status and problem detail
   */
  private ResponseEntity<ProblemDetail> notImplemented(String operation) {
    ProblemDetail problem = new ProblemDetail(
        operation + " operation not yet implemented",
        HttpStatus.NOT_IMPLEMENTED.value(),
        "not_implemented"
    );
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(problem);
  }
}
