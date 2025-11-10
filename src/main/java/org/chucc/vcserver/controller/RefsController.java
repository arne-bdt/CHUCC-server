package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.chucc.vcserver.controller.util.ResponseHeaderBuilder;
import org.chucc.vcserver.controller.util.VersionControlUrls;
import org.chucc.vcserver.dto.RefsListResponse;
import org.chucc.vcserver.service.RefService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for refs (branches and tags) management endpoints.
 */
@RestController
@RequestMapping("/{dataset}/version")
@Tag(name = "Version Control", description = "Refs management operations")
public class RefsController {

  private static final int MAX_LIMIT = 1000;

  private final RefService refService;

  /**
   * Constructor for RefsController.
   *
   * @param refService the ref service
   */
  public RefsController(RefService refService) {
    this.refService = refService;
  }

  /**
   * List all refs (branches and tags) with pagination.
   *
   * @param dataset the dataset name
   * @param limit maximum number of results to return
   * @param offset number of results to skip
   * @return paginated list of all refs
   */
  @GetMapping(value = "/refs", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List all refs",
      description = "Returns a paginated list of all branches and tags with their target commits"
  )
  @ApiResponse(
      responseCode = "200",
      description = "Refs list returned successfully",
      headers = {
          @Header(
              name = "Content-Location",
              description = "Canonical URL for this resource",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "Link",
              description = "RFC 5988 pagination links (next page when available)",
              schema = @Schema(type = "string")
          )
      },
      content = @Content(
          mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @Schema(implementation = RefsListResponse.class)
      )
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request - Invalid pagination parameters",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<RefsListResponse> listRefs(
      @Parameter(description = "Dataset name", required = true)
      @PathVariable String dataset,
      @Parameter(description = "Maximum number of results (max 1000)", example = "100")
      @RequestParam(required = false, defaultValue = "100") Integer limit,
      @Parameter(description = "Offset for pagination", example = "0")
      @RequestParam(required = false, defaultValue = "0") Integer offset
  ) {
    // Validate pagination parameters
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
    }
    if (offset < 0) {
      throw new IllegalArgumentException("Offset cannot be negative");
    }

    // Call service with pagination
    RefsListResponse response = refService.getAllRefs(dataset, limit, offset);

    // Build headers
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addContentLocation(headers, VersionControlUrls.refs(dataset));

    // Add Link header for next page (RFC 5988)
    if (response.pagination().hasMore()) {
      String nextUrl = buildNextPageUrl(dataset, limit, offset);
      headers.add("Link", String.format("<%s>; rel=\"next\"", nextUrl));
    }

    return ResponseEntity.ok().headers(headers).body(response);
  }

  /**
   * Builds the URL for the next page in pagination.
   *
   * @param dataset dataset name
   * @param limit page limit
   * @param offset current offset
   * @return URL for next page
   */
  private String buildNextPageUrl(String dataset, int limit, int offset) {
    return String.format("/%s/version/refs?offset=%d&limit=%d",
        dataset, offset + limit, limit);
  }
}
