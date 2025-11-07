package org.chucc.vcserver.controller;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryParseException;
import org.chucc.vcserver.command.SparqlUpdateCommand;
import org.chucc.vcserver.command.SparqlUpdateCommandHandler;
import org.chucc.vcserver.controller.util.ResponseHeaderBuilder;
import org.chucc.vcserver.controller.util.SparqlControllerUtil;
import org.chucc.vcserver.controller.util.SparqlExecutionHelper;
import org.chucc.vcserver.controller.util.VersionControlUrls;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.ResultFormat;
import org.chucc.vcserver.dto.ProblemDetail;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.exception.CommitNotFoundException;
import org.chucc.vcserver.exception.TagNotFoundException;
import org.chucc.vcserver.repository.TagRepository;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.SelectorResolutionService;
import org.chucc.vcserver.service.SparqlQueryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Versioned SPARQL query and update endpoints.
 *
 * <p>Supports querying and updating at specific version references:
 * <ul>
 *   <li><b>Branch:</b> {@code /{dataset}/version/branches/{name}/sparql}</li>
 *   <li><b>Commit:</b> {@code /{dataset}/version/commits/{id}/sparql} (read-only)</li>
 *   <li><b>Tag:</b> {@code /{dataset}/version/tags/{name}/sparql} (read-only)</li>
 * </ul>
 *
 * @see <a href="docs/architecture/semantic-routing.md">Semantic Routing</a>
 */
@Tag(name = "SPARQL Protocol - Versioned", description = "SPARQL operations at specific versions")
@RestController
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class VersionedSparqlController {

  private final SparqlQueryService queryService;
  private final SparqlUpdateCommandHandler updateCommandHandler;
  private final TagRepository tagRepository;
  private final DatasetService datasetService;
  private final SelectorResolutionService selectorResolutionService;

  /**
   * Constructs a VersionedSparqlController with required dependencies.
   *
   * @param queryService service for executing SPARQL queries
   * @param updateCommandHandler handler for SPARQL UPDATE operations
   * @param tagRepository repository for tag management
   * @param datasetService service for materializing datasets
   * @param selectorResolutionService service for resolving selectors to commit IDs
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring-managed beans, not modified")
  public VersionedSparqlController(
      SparqlQueryService queryService,
      SparqlUpdateCommandHandler updateCommandHandler,
      TagRepository tagRepository,
      DatasetService datasetService,
      SelectorResolutionService selectorResolutionService) {
    this.queryService = queryService;
    this.updateCommandHandler = updateCommandHandler;
    this.tagRepository = tagRepository;
    this.datasetService = datasetService;
    this.selectorResolutionService = selectorResolutionService;
  }

  // ==================== Query at Branch ====================

  /**
   * Execute SPARQL query at branch HEAD.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @param query SPARQL query string
   * @param asOf Time-travel timestamp (optional)
   * @param request HTTP servlet request for Accept header processing
   * @return Query results
   */
  @Operation(
      summary = "Query dataset at branch",
      description = "Execute SPARQL query against branch HEAD. Supports time-travel with asOf "
          + "parameter."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Query executed successfully",
          headers = {
              @Header(
                  name = "ETag",
                  description = "Commit ID of the queried state",
                  schema = @Schema(type = "string")
              ),
              @Header(
                  name = "Content-Location",
                  description = "Canonical URL of this query endpoint",
                  schema = @Schema(type = "string")
              ),
              @Header(
                  name = "Link",
                  description = "Links to related resources (branch, commit)",
                  schema = @Schema(type = "string")
              )
          }
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid SPARQL query",
          content = @Content(mediaType = "application/problem+json")
      ),
      @ApiResponse(
          responseCode = "404",
          description = "Branch or dataset not found",
          content = @Content(mediaType = "application/problem+json")
      )
  })
  @GetMapping("/{dataset}/version/branches/{branch}/sparql")
  public ResponseEntity<String> queryAtBranch(
      @Parameter(description = "Dataset name", example = "default", required = true)
      @PathVariable String dataset,

      @Parameter(description = "Branch name", example = "main", required = true)
      @PathVariable String branch,

      @Parameter(description = "SPARQL query", required = true)
      @RequestParam String query,

      @Parameter(
          description = "Time-travel timestamp (ISO 8601)",
          example = "2025-11-03T10:00:00Z"
      )
      @RequestParam(required = false) OffsetDateTime asOf,

      HttpServletRequest request) {

    try {
      // Resolve branch to commit (with optional time-travel)
      CommitId targetCommit = selectorResolutionService.resolve(
          dataset, branch, null, asOf != null ? asOf.toString() : null);

      // Materialize dataset at that commit
      Dataset materializedDataset = datasetService.materializeAtCommit(dataset, targetCommit);

      // Determine result format from Accept header
      ResultFormat format = SparqlControllerUtil.determineResultFormat(request.getHeader("Accept"));

      // Execute query
      final String results = queryService.executeQuery(materializedDataset, query, format);

      // Build response headers
      HttpHeaders headers = new HttpHeaders();
      ResponseHeaderBuilder.addContentLocation(
          headers,
          VersionControlUrls.sparqlAtBranch(dataset, branch)
      );
      ResponseHeaderBuilder.addBranchLink(headers, dataset, branch);
      ResponseHeaderBuilder.addCommitLink(headers, dataset, targetCommit.value());

      return ResponseEntity.ok()
          .headers(headers)
          .eTag("\"" + targetCommit.value() + "\"")
          .contentType(SparqlControllerUtil.getMediaType(format))
          .body(results);

    } catch (Exception e) {
      return handleQueryException(e);
    }
  }

  /**
   * Execute SPARQL query at branch HEAD (POST form).
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @param query SPARQL query string (from POST body)
   * @param asOf Time-travel timestamp (optional)
   * @param request HTTP servlet request for Accept header processing
   * @return Query results
   */
  @PostMapping(
      value = "/{dataset}/version/branches/{branch}/sparql",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
  )
  public ResponseEntity<String> queryAtBranchPost(
      @PathVariable String dataset,
      @PathVariable String branch,
      @RequestParam String query,
      @RequestParam(required = false) OffsetDateTime asOf,
      HttpServletRequest request) {

    // Delegate to GET handler
    return queryAtBranch(dataset, branch, query, asOf, request);
  }

  // ==================== Query at Commit ====================

  /**
   * Execute SPARQL query at specific commit (immutable).
   *
   * @param dataset Dataset name
   * @param commitId Commit ID (UUIDv7)
   * @param query SPARQL query string
   * @param request HTTP servlet request for Accept header processing
   * @return Query results
   */
  @Operation(
      summary = "Query dataset at commit",
      description = "Execute SPARQL query against specific commit. **Immutable** - perfect for "
          + "citations!"
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Query executed successfully",
          headers = {
              @Header(
                  name = "ETag",
                  description = "Commit ID (same as path parameter)",
                  schema = @Schema(type = "string")
              ),
              @Header(
                  name = "Cache-Control",
                  description = "public, max-age=31536000, immutable",
                  schema = @Schema(type = "string")
              ),
              @Header(
                  name = "Content-Location",
                  description = "Canonical URL of this query endpoint",
                  schema = @Schema(type = "string")
              ),
              @Header(
                  name = "Link",
                  description = "Link to commit metadata",
                  schema = @Schema(type = "string")
              )
          }
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid SPARQL query or commit ID",
          content = @Content(mediaType = "application/problem+json")
      ),
      @ApiResponse(
          responseCode = "404",
          description = "Commit or dataset not found",
          content = @Content(mediaType = "application/problem+json")
      )
  })
  @GetMapping("/{dataset}/version/commits/{commitId}/sparql")
  public ResponseEntity<String> queryAtCommit(
      @Parameter(description = "Dataset name", example = "default", required = true)
      @PathVariable String dataset,

      @Parameter(
          description = "Commit ID (UUIDv7)",
          example = "01936d8f-1234-7890-abcd-ef1234567890",
          required = true
      )
      @PathVariable String commitId,

      @Parameter(description = "SPARQL query", required = true)
      @RequestParam String query,

      HttpServletRequest request) {

    try {
      // Parse and validate commit ID
      CommitId targetCommit = CommitId.of(commitId);

      // Materialize dataset at commit
      Dataset materializedDataset = datasetService.materializeAtCommit(dataset, targetCommit);

      // Determine result format from Accept header
      ResultFormat format = SparqlControllerUtil.determineResultFormat(request.getHeader("Accept"));

      // Execute query
      final String results = queryService.executeQuery(materializedDataset, query, format);

      // Build response headers
      HttpHeaders headers = new HttpHeaders();
      ResponseHeaderBuilder.addContentLocation(
          headers,
          VersionControlUrls.sparqlAtCommit(dataset, commitId)
      );
      ResponseHeaderBuilder.addCommitLink(headers, dataset, commitId);

      // Add Cache-Control header (commits are immutable!)
      headers.setCacheControl("public, max-age=31536000, immutable");

      return ResponseEntity.ok()
          .headers(headers)
          .eTag("\"" + commitId + "\"")
          .contentType(SparqlControllerUtil.getMediaType(format))
          .body(results);

    } catch (Exception e) {
      return handleQueryException(e);
    }
  }

  /**
   * Execute SPARQL query at commit (POST form).
   *
   * @param dataset Dataset name
   * @param commitId Commit ID
   * @param query SPARQL query string
   * @param request HTTP servlet request for Accept header processing
   * @return Query results
   */
  @PostMapping(
      value = "/{dataset}/version/commits/{commitId}/sparql",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
  )
  public ResponseEntity<String> queryAtCommitPost(
      @PathVariable String dataset,
      @PathVariable String commitId,
      @RequestParam String query,
      HttpServletRequest request) {

    return queryAtCommit(dataset, commitId, query, request);
  }

  // ==================== Query at Tag ====================

  /**
   * Execute SPARQL query at tagged version.
   *
   * @param dataset Dataset name
   * @param tag Tag name
   * @param query SPARQL query string
   * @param request HTTP servlet request for Accept header processing
   * @return Query results
   */
  @Operation(
      summary = "Query dataset at tag",
      description = "Execute SPARQL query against tagged version."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Query executed successfully",
          headers = {
              @Header(
                  name = "ETag",
                  description = "Commit ID that this tag points to",
                  schema = @Schema(type = "string")
              ),
              @Header(
                  name = "Content-Location",
                  description = "Canonical URL of this query endpoint",
                  schema = @Schema(type = "string")
              ),
              @Header(
                  name = "Link",
                  description = "Links to tag and commit metadata",
                  schema = @Schema(type = "string")
              )
          }
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid SPARQL query",
          content = @Content(mediaType = "application/problem+json")
      ),
      @ApiResponse(
          responseCode = "404",
          description = "Tag or dataset not found",
          content = @Content(mediaType = "application/problem+json")
      )
  })
  @GetMapping("/{dataset}/version/tags/{tag}/sparql")
  public ResponseEntity<String> queryAtTag(
      @Parameter(description = "Dataset name", example = "default", required = true)
      @PathVariable String dataset,

      @Parameter(description = "Tag name", example = "v1.0", required = true)
      @PathVariable String tag,

      @Parameter(description = "SPARQL query", required = true)
      @RequestParam String query,

      HttpServletRequest request) {

    try {
      // Resolve tag to commit
      org.chucc.vcserver.domain.Tag tagObj = tagRepository.findByDatasetAndName(dataset, tag)
          .orElseThrow(() -> new TagNotFoundException(tag));

      CommitId commitId = tagObj.commitId();

      // Materialize dataset at commit
      Dataset materializedDataset = datasetService.materializeAtCommit(dataset, commitId);

      // Determine result format from Accept header
      ResultFormat format = SparqlControllerUtil.determineResultFormat(request.getHeader("Accept"));

      // Execute query
      final String results = queryService.executeQuery(materializedDataset, query, format);

      // Build response headers
      HttpHeaders headers = new HttpHeaders();
      ResponseHeaderBuilder.addContentLocation(
          headers,
          VersionControlUrls.sparqlAtTag(dataset, tag)
      );
      ResponseHeaderBuilder.addTagLink(headers, dataset, tag);
      ResponseHeaderBuilder.addCommitLink(headers, dataset, commitId.value());

      return ResponseEntity.ok()
          .headers(headers)
          .eTag("\"" + commitId.value() + "\"")
          .contentType(SparqlControllerUtil.getMediaType(format))
          .body(results);

    } catch (Exception e) {
      return handleQueryException(e);
    }
  }

  /**
   * Execute SPARQL query at tag (POST form).
   *
   * @param dataset Dataset name
   * @param tag Tag name
   * @param query SPARQL query string
   * @param request HTTP servlet request for Accept header processing
   * @return Query results
   */
  @PostMapping(
      value = "/{dataset}/version/tags/{tag}/sparql",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
  )
  public ResponseEntity<String> queryAtTagPost(
      @PathVariable String dataset,
      @PathVariable String tag,
      @RequestParam String query,
      HttpServletRequest request) {

    return queryAtTag(dataset, tag, query, request);
  }

  // ==================== Update at Branch ====================

  /**
   * Execute SPARQL update at branch (creates commit).
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @param update SPARQL update string
   * @param author Commit author (header)
   * @param message Commit message (header, optional)
   * @param ifMatch If-Match header for optimistic locking (optional)
   * @return Update response with commit ID
   */
  @Operation(
      summary = "Update dataset at branch",
      description = "Execute SPARQL update against branch HEAD. Creates new commit automatically."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "202",
          description = "Update accepted, commit created",
          headers = {
              @Header(
                  name = "ETag",
                  description = "New commit ID",
                  schema = @Schema(type = "string")
              ),
              @Header(
                  name = "Location",
                  description = "URI of created commit",
                  schema = @Schema(type = "string")
              ),
              @Header(
                  name = "Content-Location",
                  description = "Canonical URL of this update endpoint",
                  schema = @Schema(type = "string")
              ),
              @Header(
                  name = "Link",
                  description = "Links to branch and commit",
                  schema = @Schema(type = "string")
              )
          }
      ),
      @ApiResponse(
          responseCode = "204",
          description = "No-op (no changes detected)"
      ),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid SPARQL update",
          content = @Content(mediaType = "application/problem+json")
      ),
      @ApiResponse(
          responseCode = "404",
          description = "Branch or dataset not found",
          content = @Content(mediaType = "application/problem+json")
      ),
      @ApiResponse(
          responseCode = "409",
          description = "Conflict (use If-Match header)",
          content = @Content(mediaType = "application/problem+json")
      ),
      @ApiResponse(
          responseCode = "412",
          description = "Precondition Failed - ETag mismatch",
          content = @Content(mediaType = "application/problem+json")
      )
  })
  @PostMapping(
      value = "/{dataset}/version/branches/{branch}/update",
      consumes = "application/sparql-update"
  )
  public ResponseEntity<String> updateAtBranch(
      @Parameter(description = "Dataset name", example = "default", required = true)
      @PathVariable String dataset,

      @Parameter(description = "Branch name", example = "main", required = true)
      @PathVariable String branch,

      @RequestBody String update,

      @Parameter(description = "Commit author (required)")
      @RequestHeader(value = "SPARQL-VC-Author", required = false) String author,

      @Parameter(description = "Commit message (optional, defaults to 'SPARQL update')")
      @RequestHeader(value = "SPARQL-VC-Message", required = false) String message,

      @Parameter(description = "Expected HEAD commit for optimistic locking (optional)")
      @RequestHeader(value = "If-Match", required = false) String ifMatch) {

    // Validate required headers
    Optional<ResponseEntity<String>> authorValidation =
        SparqlExecutionHelper.validateUpdateHeaders(author);
    if (authorValidation.isPresent()) {
      return authorValidation.get();
    }

    // Parse If-Match header (optional)
    SparqlExecutionHelper.Either<ResponseEntity<String>, Optional<CommitId>> ifMatchResult =
        SparqlExecutionHelper.parseIfMatchHeader(ifMatch);
    if (ifMatchResult.isLeft()) {
      return ifMatchResult.getLeft();
    }
    Optional<CommitId> expectedHead = ifMatchResult.getRight();

    // Create and execute command
    SparqlUpdateCommand command = new SparqlUpdateCommand(
        dataset,
        branch,
        update,
        author,
        message != null ? message : "SPARQL update",
        expectedHead
    );

    // Execute update via helper (handles all exception cases)
    ResponseEntity<String> response = SparqlExecutionHelper.executeUpdate(
        command, updateCommandHandler);

    // Add custom headers for success responses
    if (response.getStatusCode() == HttpStatus.ACCEPTED) {
      HttpHeaders headers = new HttpHeaders();
      headers.putAll(response.getHeaders());
      ResponseHeaderBuilder.addContentLocation(
          headers,
          VersionControlUrls.updateAtBranch(dataset, branch)
      );
      ResponseHeaderBuilder.addBranchLink(headers, dataset, branch);

      String etag = response.getHeaders().getETag();
      if (etag != null) {
        final String commitId = etag.replace("\"", "");
        ResponseHeaderBuilder.addCommitLink(headers, dataset, commitId);
        // Add Location header with dataset-in-path format
        headers.setLocation(
            java.net.URI.create(VersionControlUrls.commit(dataset, commitId))
        );
      }

      return ResponseEntity.accepted()
          .headers(headers)
          .body(response.getBody());
    }

    return response;
  }

  // ==================== Helper Methods ====================

  /**
   * Handles common query exceptions and returns error response.
   *
   * @param e the exception to handle
   * @return error response entity
   */
  @SuppressWarnings("PMD.AvoidDuplicateLiterals") // Duplicate error codes are acceptable
  private ResponseEntity<String> handleQueryException(Exception e) {
    if (e instanceof QueryParseException) {
      return ResponseEntity
          .status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(SparqlControllerUtil.serializeProblemDetail(new ProblemDetail(
              "SPARQL query is malformed: " + e.getMessage(),
              HttpStatus.BAD_REQUEST.value(),
              "MALFORMED_QUERY")));
    } else if (e instanceof BranchNotFoundException
        || e instanceof CommitNotFoundException
        || e instanceof TagNotFoundException) {
      return ResponseEntity
          .status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(SparqlControllerUtil.serializeProblemDetail(new ProblemDetail(
              e.getMessage(),
              HttpStatus.NOT_FOUND.value(),
              "NOT_FOUND")));
    } else if (e instanceof IllegalArgumentException) {
      return ResponseEntity
          .status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(SparqlControllerUtil.serializeProblemDetail(new ProblemDetail(
              e.getMessage(),
              HttpStatus.BAD_REQUEST.value(),
              "INVALID_REQUEST")));
    }
    // Return generic error for unexpected exceptions
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(SparqlControllerUtil.serializeProblemDetail(new ProblemDetail(
            "Internal server error: " + e.getMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "INTERNAL_ERROR")));
  }

}
