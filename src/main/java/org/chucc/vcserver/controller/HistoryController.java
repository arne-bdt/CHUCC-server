package org.chucc.vcserver.controller;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.dto.BlameResponse;
import org.chucc.vcserver.dto.HistoryResponse;
import org.chucc.vcserver.dto.ProblemDetail;
import org.chucc.vcserver.service.BlameService;
import org.chucc.vcserver.service.DiffService;
import org.chucc.vcserver.service.HistoryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * History and diff endpoints for version control.
 */
@RestController
@RequestMapping("/version")
@Tag(name = "Version Control", description = "History and diff operations")
public class HistoryController {

  private static final int MAX_LIMIT = 1000;
  private static final String MEDIA_TYPE_RDF_PATCH = "text/rdf-patch";

  private final VersionControlProperties vcProperties;
  private final HistoryService historyService;
  private final DiffService diffService;
  private final BlameService blameService;

  /**
   * Constructs a HistoryController.
   *
   * @param vcProperties the version control configuration properties
   * @param historyService the history service
   * @param diffService the diff service
   * @param blameService the blame service
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "VersionControlProperties, HistoryService, DiffService, and BlameService "
          + "are Spring-managed beans"
  )
  public HistoryController(VersionControlProperties vcProperties,
      HistoryService historyService,
      DiffService diffService,
      BlameService blameService) {
    this.vcProperties = vcProperties;
    this.historyService = historyService;
    this.diffService = diffService;
    this.blameService = blameService;
  }

  /**
   * List commit history with filters and pagination.
   *
   * @param dataset dataset name (required)
   * @param branch target branch (optional)
   * @param limit limit number of results (default 100)
   * @param offset offset for pagination (default 0)
   * @param since since timestamp (RFC3339/ISO8601, optional)
   * @param until until timestamp (RFC3339/ISO8601, optional)
   * @param author filter by author (optional)
   * @return history response with commits and pagination metadata
   */
  @GetMapping(value = "/history", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List history",
      description = "List commit history with filters and pagination")
  @ApiResponse(
      responseCode = "200",
      description = "History",
      headers = @Header(
          name = "Link",
          description = "RFC 5988 pagination links",
          schema = @Schema(type = "string")
      ),
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request - Missing dataset parameter or invalid date format",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Not Found - Dataset or branch not found",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<HistoryResponse> listHistory(
      @Parameter(description = "Dataset name", required = true)
      @RequestParam String dataset,
      @Parameter(description = "Target branch")
      @RequestParam(required = false) String branch,
      @Parameter(description = "Limit number of results (max 1000)")
      @RequestParam(required = false, defaultValue = "100") Integer limit,
      @Parameter(description = "Offset for pagination")
      @RequestParam(required = false, defaultValue = "0") Integer offset,
      @Parameter(description = "Since timestamp (RFC3339/ISO8601)")
      @RequestParam(required = false) String since,
      @Parameter(description = "Until timestamp (RFC3339/ISO8601)")
      @RequestParam(required = false) String until,
      @Parameter(description = "Filter by author")
      @RequestParam(required = false) String author
  ) {
    // Validate dataset parameter
    if (dataset == null || dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset parameter is required");
    }

    // Validate pagination parameters
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
    }
    if (offset < 0) {
      throw new IllegalArgumentException("Offset cannot be negative");
    }

    // Parse date parameters (RFC3339/ISO8601)
    Instant sinceInstant = null;
    Instant untilInstant = null;
    try {
      if (since != null) {
        sinceInstant = Instant.parse(since);
      }
      if (until != null) {
        untilInstant = Instant.parse(until);
      }
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Invalid date format: " + e.getMessage(), e);
    }

    // Call service
    HistoryResponse response = historyService.listHistory(
        dataset, branch, sinceInstant, untilInstant, author, limit, offset
    );

    // Build Link header for next page (RFC 5988)
    ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();
    if (response.pagination().hasMore()) {
      String nextUrl = buildNextPageUrl(dataset, branch, since, until, author, limit, offset);
      responseBuilder.header("Link", String.format("<%s>; rel=\"next\"", nextUrl));
    }

    return responseBuilder.body(response);
  }

  /**
   * Builds the URL for the next page in pagination.
   *
   * @param dataset dataset name
   * @param branch branch name (optional)
   * @param since since timestamp (optional)
   * @param until until timestamp (optional)
   * @param author author filter (optional)
   * @param limit page limit
   * @param offset current offset
   * @return URL for next page
   */
  private String buildNextPageUrl(String dataset, String branch, String since,
      String until, String author, int limit, int offset) {
    StringBuilder url = new StringBuilder("/version/history?dataset=");
    url.append(urlEncode(dataset));
    url.append("&offset=").append(offset + limit);
    url.append("&limit=").append(limit);

    if (branch != null) {
      url.append("&branch=").append(urlEncode(branch));
    }
    if (since != null) {
      url.append("&since=").append(urlEncode(since));
    }
    if (until != null) {
      url.append("&until=").append(urlEncode(until));
    }
    if (author != null) {
      url.append("&author=").append(urlEncode(author));
    }

    return url.toString();
  }

  /**
   * URL-encodes a string parameter.
   *
   * @param value the value to encode
   * @return URL-encoded value
   */
  private String urlEncode(String value) {
    try {
      return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException e) {
      // UTF-8 is always supported
      return value;
    }
  }

  /**
   * Diff two commits.
   *
   * @param dataset dataset name
   * @param from from commit id
   * @param to to commit id
   * @return RDF Patch representing changes from 'from' to 'to'
   */
  @GetMapping(value = "/diff", produces = MEDIA_TYPE_RDF_PATCH)
  @Operation(
      summary = "Diff two commits",
      description = "Get RDF Patch representing changes from 'from' commit to 'to' commit. "
          + "Additions are quads in 'to' but not in 'from', "
          + "deletions are quads in 'from' but not in 'to'. "
          + "⚠️ EXTENSION: This endpoint is not part of the official "
          + "SPARQL 1.2 Protocol specification."
  )
  @ApiResponse(
      responseCode = "200",
      description = "RDF Patch representing changes between commits",
      content = @Content(mediaType = MEDIA_TYPE_RDF_PATCH)
  )
  @ApiResponse(
      responseCode = "400",
      description = "Invalid request parameters",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Commit not found or diff endpoint disabled",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<String> diffCommits(
      @Parameter(description = "Dataset name", required = true)
      @RequestParam String dataset,
      @Parameter(description = "From commit id (UUIDv7)", required = true)
      @RequestParam String from,
      @Parameter(description = "To commit id (UUIDv7)", required = true)
      @RequestParam String to
  ) {
    // Check feature flag
    if (!vcProperties.isDiffEnabled()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              "Diff endpoint is disabled",
              HttpStatus.NOT_FOUND.value(),
              "NOT_FOUND"
          ).toString());
    }

    // Validate dataset parameter
    if (dataset == null || dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset parameter is required");
    }

    try {
      // Parse commit IDs
      CommitId fromCommitId = CommitId.of(from);
      CommitId toCommitId = CommitId.of(to);

      // Call service
      String patchText = diffService.diffCommits(dataset, fromCommitId, toCommitId);

      // Return RDF Patch
      return ResponseEntity.ok()
          .contentType(MediaType.parseMediaType(MEDIA_TYPE_RDF_PATCH))
          .body(patchText);

    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid commit ID format: " + e.getMessage(), e);
    } catch (org.chucc.vcserver.exception.CommitNotFoundException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              e.getMessage(),
              HttpStatus.NOT_FOUND.value(),
              "NOT_FOUND"
          ).toString());
    }
  }

  /**
   * Get last-writer attribution for all quads in a specific graph.
   * Like 'git blame' for a file, this operates on one graph at a time.
   *
   * @param dataset dataset name (required)
   * @param commit commit ID to blame (required)
   * @param graph graph IRI to blame (required, use "default" for default graph)
   * @param offset number of results to skip (default 0)
   * @param limit maximum results per page (default 100, max 1000)
   * @return blame response with quad attribution and pagination
   */
  @GetMapping(value = "/blame", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Last-writer attribution for graph",
      description = "Get last-writer attribution for all quads in a specific graph. "
          + "Like 'git blame' for a single file, this operates on one graph at a time. "
          + "⚠️ EXTENSION: This endpoint is not part of the official "
          + "SPARQL 1.2 Protocol specification."
  )
  @Parameter(name = "dataset", description = "Dataset name", required = true)
  @Parameter(name = "commit", description = "Commit ID (UUIDv7)", required = true)
  @Parameter(name = "graph", description = "Graph IRI (use 'default' for default graph)",
      required = true)
  @Parameter(name = "offset", description = "Number of results to skip (default: 0)")
  @Parameter(name = "limit", description = "Max results per page (default: 100, max: 1000)")
  @ApiResponse(
      responseCode = "200",
      description = "Blame information with quad attribution",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE),
      headers = @Header(
          name = "Link",
          description = "RFC 5988 pagination links (next page when available)",
          schema = @Schema(type = "string")
      )
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request (missing parameters or invalid limit)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Not Found (blame disabled, commit not found, or graph not found)",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<BlameResponse> blameGraph(
      @RequestParam String dataset,
      @RequestParam String commit,
      @RequestParam String graph,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "100") int limit
  ) {
    // Feature flag check
    if (!vcProperties.isBlameEnabled()) {
      throw new IllegalStateException("Blame endpoint is disabled");
    }

    // Validate required parameters
    if (dataset == null || dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset parameter is required");
    }
    if (commit == null || commit.isBlank()) {
      throw new IllegalArgumentException("Commit parameter is required");
    }
    if (graph == null || graph.isBlank()) {
      throw new IllegalArgumentException("Graph parameter is required");
    }

    // Validate pagination
    if (offset < 0) {
      throw new IllegalArgumentException("Offset cannot be negative");
    }
    if (limit < 1) {
      throw new IllegalArgumentException("Limit must be at least 1");
    }
    if (limit > MAX_LIMIT) {
      throw new IllegalArgumentException("Limit cannot exceed " + MAX_LIMIT);
    }

    // Parse commit ID
    CommitId commitId = CommitId.of(commit);

    // Call service
    BlameResponse response = blameService.blameGraph(dataset, commitId, graph, offset, limit);

    // Build Link header if hasMore=true (RFC 5988)
    HttpHeaders headers = new HttpHeaders();
    if (response.pagination().hasMore()) {
      String nextUrl = buildBlameNextUrl(dataset, commit, graph, offset + limit, limit);
      headers.add("Link", String.format("<%s>; rel=\"next\"", nextUrl));
    }

    return ResponseEntity.ok().headers(headers).body(response);
  }

  /**
   * Builds the next page URL for blame pagination.
   *
   * @param dataset dataset name
   * @param commit commit ID
   * @param graph graph IRI
   * @param offset next offset
   * @param limit limit per page
   * @return next page URL
   */
  private String buildBlameNextUrl(String dataset, String commit, String graph,
      int offset, int limit) {
    return String.format("/version/blame?dataset=%s&commit=%s&graph=%s&offset=%d&limit=%d",
        urlEncode(dataset),
        urlEncode(commit),
        urlEncode(graph),
        offset,
        limit);
  }
}
