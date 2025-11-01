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
import org.chucc.vcserver.dto.HistoryResponse;
import org.chucc.vcserver.dto.ProblemDetail;
import org.chucc.vcserver.service.HistoryService;
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

  private final VersionControlProperties vcProperties;
  private final HistoryService historyService;

  /**
   * Constructs a HistoryController.
   *
   * @param vcProperties the version control configuration properties
   * @param historyService the history service
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "VersionControlProperties and HistoryService are Spring-managed beans"
  )
  public HistoryController(VersionControlProperties vcProperties,
      HistoryService historyService) {
    this.vcProperties = vcProperties;
    this.historyService = historyService;
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
   * @param from from commit id
   * @param to to commit id
   * @return changeset (501 stub)
   */
  @GetMapping(value = "/diff", produces = "text/rdf-patch")
  @Operation(
      summary = "Diff two commits",
      description = "Get changeset between two commits. "
          + "⚠️ EXTENSION: This endpoint is not part of the official "
          + "SPARQL 1.2 Protocol specification."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Changeset between from→to",
      content = @Content(mediaType = "text/rdf-patch")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Commit not found",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "501",
      description = "Not Implemented",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<String> diffCommits(
      @Parameter(description = "From commit id (UUIDv7)", required = true)
      @RequestParam String from,
      @Parameter(description = "To commit id (UUIDv7)", required = true)
      @RequestParam String to
  ) {
    if (!vcProperties.isDiffEnabled()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              "Diff endpoint is disabled",
              HttpStatus.NOT_FOUND.value(),
              "NOT_FOUND"
          ).toString());
    }

    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body("{\"title\":\"Not Implemented\",\"status\":501}");
  }

  /**
   * Get last-writer attribution for a resource.
   *
   * @param subject subject IRI
   * @return blame info (501 stub)
   */
  @GetMapping(value = "/blame", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Last-writer attribution",
      description = "Get last-writer attribution for a resource. "
          + "⚠️ EXTENSION: This endpoint is not part of the official "
          + "SPARQL 1.2 Protocol specification."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Blame/annotate info",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "404",
      description = "Resource not found",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "501",
      description = "Not Implemented",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<String> blameResource(
      @Parameter(description = "Subject IRI", required = true)
      @RequestParam String subject
  ) {
    if (!vcProperties.isBlameEnabled()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              "Blame endpoint is disabled",
              HttpStatus.NOT_FOUND.value(),
              "NOT_FOUND"
          ).toString());
    }

    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body("{\"title\":\"Not Implemented\",\"status\":501}");
  }
}
