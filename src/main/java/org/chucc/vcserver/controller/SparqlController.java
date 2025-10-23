package org.chucc.vcserver.controller;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryParseException;
import org.chucc.vcserver.command.SparqlUpdateCommand;
import org.chucc.vcserver.command.SparqlUpdateCommandHandler;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.ResultFormat;
import org.chucc.vcserver.dto.ProblemDetail;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.exception.CommitNotFoundException;
import org.chucc.vcserver.exception.MalformedUpdateException;
import org.chucc.vcserver.exception.PreconditionFailedException;
import org.chucc.vcserver.exception.UpdateExecutionException;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.SelectorResolutionService;
import org.chucc.vcserver.service.SparqlQueryService;
import org.chucc.vcserver.util.SelectorValidator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SPARQL 1.2 Protocol endpoint with version control support.
 */
@RestController
@RequestMapping("/sparql")
@Tag(name = "SPARQL Protocol", description = "SPARQL 1.2 query and update operations")
public class SparqlController {

  private final VersionControlProperties vcProperties;
  private final SelectorResolutionService selectorResolutionService;
  private final DatasetService datasetService;
  private final SparqlQueryService sparqlQueryService;
  private final SparqlUpdateCommandHandler sparqlUpdateCommandHandler;

  /**
   * Constructs a SparqlController with required dependencies.
   *
   * @param vcProperties version control properties
   * @param selectorResolutionService service for resolving selectors to commit IDs
   * @param datasetService service for materializing datasets
   * @param sparqlQueryService service for executing SPARQL queries
   * @param sparqlUpdateCommandHandler handler for SPARQL UPDATE operations
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring-managed beans, not modified")
  public SparqlController(
      VersionControlProperties vcProperties,
      SelectorResolutionService selectorResolutionService,
      DatasetService datasetService,
      SparqlQueryService sparqlQueryService,
      SparqlUpdateCommandHandler sparqlUpdateCommandHandler) {
    this.vcProperties = vcProperties;
    this.selectorResolutionService = selectorResolutionService;
    this.datasetService = datasetService;
    this.sparqlQueryService = sparqlQueryService;
    this.sparqlUpdateCommandHandler = sparqlUpdateCommandHandler;
  }

  /**
   * Execute a SPARQL query via HTTP GET.
   *
   * @param query SPARQL query string
   * @param defaultGraphUri default graph URIs
   * @param namedGraphUri named graph URIs
   * @param branch target branch
   * @param commit target commit
   * @param asOf timestamp for time-travel query
   * @param vcCommit commit ID for read consistency (header)
   * @param request the HTTP servlet request for Accept header processing
   * @return query results with ETag header containing commit ID
   * @throws BranchNotFoundException if specified branch does not exist
   * @throws CommitNotFoundException if specified commit does not exist or
   *     no commit found at/before asOf timestamp
   * @throws IllegalArgumentException if query is malformed or selectors conflict
   * @throws QueryParseException if SPARQL query syntax is invalid
   */
  @GetMapping(produces = {
      "application/sparql-results+json",
      "application/sparql-results+xml",
      "text/csv",
      "text/tab-separated-values",
      "text/turtle",
      "application/rdf+xml"
  })
  @Operation(
      summary = "SPARQL Query (GET)",
      description = "Execute a SPARQL query using HTTP GET. "
          + "Version control: Query parameters branch, commit, or asOf select the dataset state. "
          + "Without these, queries execute against the default branch HEAD."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Query results",
      headers = @Header(
          name = "ETag",
          description = "Commit id of the queried state",
          schema = @Schema(type = "string")
      )
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "501",
      description = "Not Implemented",
      content = @Content(mediaType = "application/problem+json")
  )
  @SuppressWarnings("PMD.UnusedFormalParameter") // Reserved parameters for future use
  public ResponseEntity<?> querySparqlGet(
      @Parameter(description = "Dataset name")
      @RequestParam(defaultValue = "default") String dataset,
      @Parameter(description = "The SPARQL query string", required = true)
      @RequestParam String query,
      @Parameter(description = "Default graph URI(s)")
      @RequestParam(name = "default-graph-uri", required = false) String[] defaultGraphUri,
      @Parameter(description = "Named graph URI(s)")
      @RequestParam(name = "named-graph-uri", required = false) String[] namedGraphUri,
      @Parameter(description = "Target branch for query")
      @RequestParam(required = false) String branch,
      @Parameter(description = "Target commit for query (read-only)")
      @RequestParam(required = false) String commit,
      @Parameter(description = "Query branch state at or before this timestamp (ISO8601)")
      @RequestParam(required = false) String asOf,
      @Parameter(description = "Commit ID for read consistency")
      @RequestHeader(name = "SPARQL-VC-Commit", required = false) String vcCommit,
      HttpServletRequest request
  ) {
    // Note: defaultGraphUri, namedGraphUri, and vcCommit are reserved for future use
    return executeQueryOperation(dataset, query, branch, commit, asOf, request);
  }

  /**
   * Handle SPARQL Query via POST with application/sparql-query.
   * Shares logic with querySparqlGet() but receives query in POST body.
   *
   * @param queryString SPARQL query from POST body
   * @param branch target branch (optional)
   * @param commit target commit (optional)
   * @param asOf timestamp for time-travel (optional)
   * @param vcCommit commit ID from SPARQL-VC-Commit header (optional)
   * @param request HTTP request for Accept header
   * @return query results with ETag
   */
  @SuppressWarnings("PMD.UnusedFormalParameter") // vcCommit reserved for future use
  private ResponseEntity<?> handleQueryViaPost(
      String dataset,
      String queryString,
      String branch,
      String commit,
      String asOf,
      String vcCommit,
      HttpServletRequest request) {
    return executeQueryOperation(dataset, queryString, branch, commit, asOf, request);
  }

  /**
   * Execute a SPARQL query operation (shared logic for GET and POST).
   *
   * @param dataset dataset name
   * @param query the SPARQL query string
   * @param branch target branch (optional)
   * @param commit target commit (optional)
   * @param asOf timestamp for time-travel (optional)
   * @param request HTTP request for Accept header
   * @return query results with ETag
   */
  @SuppressWarnings("PMD.AvoidDuplicateLiterals") // Duplicate error codes are acceptable
  private ResponseEntity<?> executeQueryOperation(
      String dataset,
      String query,
      String branch,
      String commit,
      String asOf,
      HttpServletRequest request) {

    // Validate selector mutual exclusion
    try {
      SelectorValidator.validateMutualExclusion(branch, commit, asOf);
    } catch (IllegalArgumentException e) {
      return ResponseEntity
          .status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(e.getMessage(), HttpStatus.BAD_REQUEST.value(),
              "SELECTOR_CONFLICT"));
    }

    // Use dataset parameter passed from caller

    try {
      // 1. Resolve selectors to target commit
      CommitId targetCommit = selectorResolutionService.resolve(
          dataset, branch, commit, asOf);

      // 2. Materialize dataset at that commit
      // Note: Dataset is a lightweight wrapper around the cached DatasetGraph.
      // The underlying DatasetGraph is managed by DatasetService cache and shared
      // across requests for the same commit. No explicit closing needed.
      Dataset materializedDataset = datasetService.materializeAtCommit(dataset, targetCommit);

      // 3. Determine result format from Accept header
      ResultFormat format = determineResultFormat(request.getHeader("Accept"));

      // 4. Execute query
      String results = sparqlQueryService.executeQuery(materializedDataset, query, format);

      // 5. Return results with ETag header containing commit ID
      return ResponseEntity.ok()
          .eTag("\"" + targetCommit.value() + "\"")
          .contentType(getMediaType(format))
          .body(results);

    } catch (QueryParseException e) {
      return ResponseEntity
          .status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              "SPARQL query is malformed: " + e.getMessage(),
              HttpStatus.BAD_REQUEST.value(),
              "MALFORMED_QUERY"));
    } catch (BranchNotFoundException | CommitNotFoundException e) {
      return ResponseEntity
          .status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(e.getMessage(), HttpStatus.NOT_FOUND.value(), "NOT_FOUND"));
    } catch (IllegalArgumentException e) {
      return ResponseEntity
          .status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(e.getMessage(), HttpStatus.BAD_REQUEST.value(),
              "INVALID_REQUEST"));
    }
  }

  /**
   * Execute a SPARQL query or update via HTTP POST.
   *
   * @param body request body (query or update)
   * @param message commit message header
   * @param author commit author header
   * @param branch target branch
   * @param ifMatch If-Match header for optimistic locking
   * @param contentType content type header
   * @param commit target commit for query
   * @param asOf time-travel timestamp for query
   * @param vcCommit commit ID for read consistency
   * @param request HTTP servlet request
   * @return query results or update confirmation
   */
  @PostMapping(
      consumes = {
          MediaType.APPLICATION_FORM_URLENCODED_VALUE,
          "application/sparql-query",
          "application/sparql-update"
      },
      produces = {
          "application/sparql-results+json",
          "application/sparql-results+xml",
          "text/csv",
          "text/tab-separated-values",
          "text/turtle",
          "application/rdf+xml",
          MediaType.APPLICATION_JSON_VALUE
      }
  )
  @Operation(
      summary = "SPARQL Query or Update (POST)",
      description = "Execute a SPARQL query or update using HTTP POST. "
          + "Content-Type application/sparql-query → Query operation. "
          + "Content-Type application/sparql-update → Update operation (creates commit). "
          + "Content-Type application/x-www-form-urlencoded → Query or update based on form fields."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Query results or update confirmation with representation",
      headers = {
          @Header(
              name = "ETag",
              description = "Commit id (new for updates, queried for queries)",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "Location",
              description = "URI of created commit (for updates)",
              schema = @Schema(type = "string")
          )
      }
  )
  @ApiResponse(
      responseCode = "204",
      description = "Update succeeded without representation",
      headers = {
          @Header(
              name = "ETag",
              description = "New commit id",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "Location",
              description = "URI of created commit",
              schema = @Schema(type = "string")
          )
      }
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "412",
      description = "Precondition Failed - ETag mismatch",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "501",
      description = "Not Implemented",
      content = @Content(mediaType = "application/problem+json")
  )
  @SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.CyclomaticComplexity",
      "PMD.ExcessiveParameterList"})
  public ResponseEntity<?> executeSparqlPost(
      @Parameter(description = "Dataset name")
      @RequestParam(defaultValue = "default") String dataset,
      @RequestBody String body,
      @Parameter(description = "Commit message (SHOULD provide for updates)")
      @RequestHeader(name = "SPARQL-VC-Message", required = false) String message,
      @Parameter(description = "Commit author (SHOULD provide for updates)")
      @RequestHeader(name = "SPARQL-VC-Author", required = false) String author,
      @Parameter(description = "Target branch header for updates (defaults to 'main')")
      @RequestHeader(name = "SPARQL-VC-Branch", required = false) String branchHeader,
      @Parameter(description = "Expected HEAD commit for optimistic locking (updates only)")
      @RequestHeader(name = "If-Match", required = false) String ifMatch,
      @RequestHeader(name = "Content-Type", required = false) String contentType,
      @Parameter(description = "Target branch query parameter for queries")
      @RequestParam(required = false) String branch,
      @Parameter(description = "Target commit for query (read-only)")
      @RequestParam(required = false) String commit,
      @Parameter(description = "Query branch state at or before this timestamp (ISO8601)")
      @RequestParam(required = false) String asOf,
      @Parameter(description = "Commit ID for read consistency (queries only)")
      @RequestHeader(name = "SPARQL-VC-Commit", required = false) String vcCommit,
      HttpServletRequest request
  ) {
    // Determine operation type from Content-Type
    boolean isUpdate = contentType != null
        && contentType.toLowerCase(java.util.Locale.ROOT)
            .contains("application/sparql-update");

    boolean isQuery = contentType != null
        && contentType.toLowerCase(java.util.Locale.ROOT)
            .contains("application/sparql-query");

    // Handle SPARQL Query via POST
    if (isQuery) {
      return handleQueryViaPost(dataset, body, branch, commit, asOf, vcCommit, request);
    }

    // If neither query nor update, return 501
    if (!isUpdate) {
      return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body("{\"title\":\"SPARQL Query or Update via POST requires "
              + "Content-Type: application/sparql-query or application/sparql-update\","
              + "\"status\":501}");
    }

    // SPARQL UPDATE operation
    // Use dataset parameter from method signature
    final String branchName = (branchHeader != null && !branchHeader.isBlank())
        ? branchHeader : "main";

    // Validate required headers for UPDATE
    if (message == null || message.isBlank()) {
      ProblemDetail problem = new ProblemDetail(
          "SPARQL-VC-Message header is required for UPDATE operations",
          HttpStatus.BAD_REQUEST.value(),
          "MISSING_HEADER");
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(serializeProblemDetail(problem));
    }

    if (author == null || author.isBlank()) {
      ProblemDetail problem = new ProblemDetail(
          "SPARQL-VC-Author header is required for UPDATE operations",
          HttpStatus.BAD_REQUEST.value(),
          "MISSING_HEADER");
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(serializeProblemDetail(problem));
    }

    // Parse If-Match header (optional)
    Optional<CommitId> expectedHead = Optional.empty();
    if (ifMatch != null && !ifMatch.isBlank()) {
      // Remove surrounding quotes if present
      String cleanEtag = ifMatch.replaceAll("^\"|\"$", "");
      try {
        expectedHead = Optional.of(CommitId.of(cleanEtag));
      } catch (IllegalArgumentException e) {
        ProblemDetail problem = new ProblemDetail(
            "Invalid If-Match header: " + e.getMessage(),
            HttpStatus.BAD_REQUEST.value(),
            "INVALID_ETAG");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(serializeProblemDetail(problem));
      }
    }

    // Create and execute command
    SparqlUpdateCommand command = new SparqlUpdateCommand(
        dataset,
        branchName,
        body,
        author,
        message,
        expectedHead
    );

    try {
      VersionControlEvent event = sparqlUpdateCommandHandler.handle(command);

      // Handle no-op (per SPARQL 1.2 Protocol §7)
      if (event == null) {
        // No-op: Return 204 No Content without body
        return ResponseEntity.noContent().build();
      }

      // Success: Return 200 with ETag and Location headers
      CommitCreatedEvent commitEvent = (CommitCreatedEvent) event;
      String commitId = commitEvent.commitId();

      return ResponseEntity.ok()
          .eTag("\"" + commitId + "\"")
          .location(java.net.URI.create("/version/datasets/" + dataset
              + "/commits/" + commitId))
          .contentType(MediaType.APPLICATION_JSON)
          .body("{\"message\":\"Update successful\",\"commitId\":\""
              + commitId + "\"}");

    } catch (MalformedUpdateException e) {
      ProblemDetail problem = new ProblemDetail(
          e.getMessage(),
          HttpStatus.BAD_REQUEST.value(),
          "MALFORMED_UPDATE");
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(serializeProblemDetail(problem));
    } catch (PreconditionFailedException e) {
      ProblemDetail problem = new ProblemDetail(
          e.getMessage(),
          HttpStatus.PRECONDITION_FAILED.value(),
          "PRECONDITION_FAILED");
      return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(serializeProblemDetail(problem));
    } catch (UpdateExecutionException e) {
      ProblemDetail problem = new ProblemDetail(
          e.getMessage(),
          HttpStatus.INTERNAL_SERVER_ERROR.value(),
          "UPDATE_EXECUTION_ERROR");
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(serializeProblemDetail(problem));
    } catch (IllegalArgumentException e) {
      ProblemDetail problem = new ProblemDetail(
          e.getMessage(),
          HttpStatus.BAD_REQUEST.value(),
          "INVALID_REQUEST");
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(serializeProblemDetail(problem));
    }
  }

  /**
   * Serializes a ProblemDetail to JSON string.
   * Simple implementation for manual JSON construction.
   *
   * @param problem the problem detail to serialize
   * @return JSON string representation
   */
  private String serializeProblemDetail(ProblemDetail problem) {
    StringBuilder json = new StringBuilder();
    json.append("{");
    json.append("\"type\":\"").append(escapeJson(problem.getType())).append("\",");
    json.append("\"title\":\"").append(escapeJson(problem.getTitle())).append("\",");
    json.append("\"status\":").append(problem.getStatus());
    if (problem.getCode() != null) {
      json.append(",\"code\":\"").append(escapeJson(problem.getCode())).append("\"");
    }
    if (problem.getDetail() != null) {
      json.append(",\"detail\":\"").append(escapeJson(problem.getDetail())).append("\"");
    }
    json.append("}");
    return json.toString();
  }

  /**
   * Escapes special characters in JSON strings.
   *
   * @param str the string to escape
   * @return the escaped string
   */
  private String escapeJson(String str) {
    if (str == null) {
      return "";
    }
    return str.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  /**
   * Determines the result format from the Accept header.
   *
   * @param acceptHeader the Accept header value
   * @return the determined result format (defaults to JSON per SPARQL 1.1 Protocol §2.1)
   */
  private ResultFormat determineResultFormat(String acceptHeader) {
    if (acceptHeader == null || acceptHeader.isEmpty()) {
      // Default to JSON per SPARQL 1.1 Protocol recommendation (section 2.1)
      // JSON is widely supported and human-readable for debugging
      return ResultFormat.JSON;
    }

    String lowerAccept = acceptHeader.toLowerCase(java.util.Locale.ROOT);

    if (lowerAccept.contains("application/sparql-results+json")) {
      return ResultFormat.JSON;
    } else if (lowerAccept.contains("application/sparql-results+xml")) {
      return ResultFormat.XML;
    } else if (lowerAccept.contains("text/csv")) {
      return ResultFormat.CSV;
    } else if (lowerAccept.contains("text/tab-separated-values")
        || lowerAccept.contains("text/tsv")) {
      return ResultFormat.TSV;
    } else if (lowerAccept.contains("text/turtle")) {
      return ResultFormat.TURTLE;
    } else if (lowerAccept.contains("application/rdf+xml")) {
      return ResultFormat.RDF_XML;
    }

    // Default to JSON when Accept header doesn't match any supported format
    // This follows the SPARQL 1.1 Protocol recommendation for content negotiation
    return ResultFormat.JSON;
  }

  /**
   * Maps result format to HTTP media type.
   *
   * @param format the result format
   * @return the corresponding media type
   */
  private MediaType getMediaType(ResultFormat format) {
    switch (format) {
      case JSON:
        return MediaType.parseMediaType("application/sparql-results+json");
      case XML:
        return MediaType.parseMediaType("application/sparql-results+xml");
      case CSV:
        return MediaType.parseMediaType("text/csv");
      case TSV:
        return MediaType.parseMediaType("text/tab-separated-values");
      case TURTLE:
        return MediaType.parseMediaType("text/turtle");
      case RDF_XML:
        return MediaType.parseMediaType("application/rdf+xml");
      default:
        return MediaType.parseMediaType("application/sparql-results+json");
    }
  }

  /**
   * Discover SPARQL endpoint capabilities via HTTP OPTIONS.
   * Advertises SPARQL-Version-Control support and enabled features.
   *
   * @return OPTIONS response with capability headers
   */
  @RequestMapping(method = RequestMethod.OPTIONS)
  @Operation(
      summary = "Discover endpoint capabilities",
      description = "Returns HTTP OPTIONS response advertising SPARQL 1.2 Protocol support, "
          + "SPARQL-Version-Control extension, conformance level, and enabled features."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Capability information",
      headers = {
          @Header(
              name = "Allow",
              description = "Allowed HTTP methods",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "Accept-Patch",
              description = "Supported patch formats (text/rdf-patch)",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "SPARQL-Version-Control",
              description = "Version control extension version",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "SPARQL-VC-Level",
              description = "Conformance level (1=Basic, 2=Advanced)",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "SPARQL-VC-Features",
              description = "Enabled features (comma-separated)",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "Link",
              description = "Link to version control API",
              schema = @Schema(type = "string")
          )
      }
  )
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<Void> options() {
    HttpHeaders headers = new HttpHeaders();

    // Standard HTTP OPTIONS
    headers.add(HttpHeaders.ALLOW, "GET, POST, OPTIONS");

    // SPARQL Version Control extension support
    headers.add("SPARQL-Version-Control", "1.0");
    headers.add("SPARQL-VC-Level", String.valueOf(vcProperties.getLevel()));

    // Advertise RDF Patch support
    if (vcProperties.isRdfPatchEnabled()) {
      headers.add(HttpHeaders.ACCEPT_PATCH, "text/rdf-patch");
    }

    // Build enabled features list
    List<String> features = new ArrayList<>();
    if (vcProperties.isCommitsEnabled()) {
      features.add("commits");
    }
    if (vcProperties.isBranchesEnabled()) {
      features.add("branches");
    }
    if (vcProperties.isHistoryEnabled()) {
      features.add("history");
    }
    if (vcProperties.isRdfPatchEnabled()) {
      features.add("rdf-patch");
    }
    if (vcProperties.isMergeEnabled()) {
      features.add("merge");
    }
    if (vcProperties.isConflictDetectionEnabled()) {
      features.add("conflict-detection");
    }
    if (vcProperties.isTagsEnabled()) {
      features.add("tags");
    }
    if (vcProperties.isRevertEnabled()) {
      features.add("revert");
    }
    if (vcProperties.isResetEnabled()) {
      features.add("reset");
    }
    if (vcProperties.isCherryPickEnabled()) {
      features.add("cherry-pick");
    }
    if (vcProperties.isBlameEnabled()) {
      features.add("blame");
    }

    if (!features.isEmpty()) {
      headers.add("SPARQL-VC-Features", String.join(", ", features));
    }

    // Link to version control API
    headers.add(HttpHeaders.LINK, "</version>; rel=\"version-control\"");

    return ResponseEntity.ok().headers(headers).build();
  }
}
