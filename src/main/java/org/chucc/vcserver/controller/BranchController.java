package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.chucc.vcserver.command.CreateBranchCommand;
import org.chucc.vcserver.command.CreateBranchCommandHandler;
import org.chucc.vcserver.command.DeleteBranchCommand;
import org.chucc.vcserver.command.DeleteBranchCommandHandler;
import org.chucc.vcserver.controller.util.ResponseHeaderBuilder;
import org.chucc.vcserver.controller.util.VersionControlUrls;
import org.chucc.vcserver.dto.BranchListResponse;
import org.chucc.vcserver.dto.CreateBranchRequest;
import org.chucc.vcserver.dto.CreateBranchResponse;
import org.chucc.vcserver.dto.ProblemDetail;
import org.chucc.vcserver.event.BranchCreatedEvent;
import org.chucc.vcserver.exception.BranchAlreadyExistsException;
import org.chucc.vcserver.exception.RefNotFoundException;
import org.chucc.vcserver.service.BranchService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Branch management endpoints for version control.
 */
@RestController
@RequestMapping("/{dataset}/version/branches")
@Tag(name = "Version Control", description = "Branch management operations")
public class BranchController {

  private static final int MAX_LIMIT = 1000;

  private final DeleteBranchCommandHandler deleteBranchCommandHandler;
  private final CreateBranchCommandHandler createBranchCommandHandler;
  private final BranchService branchService;

  /**
   * Constructs a BranchController.
   *
   * @param deleteBranchCommandHandler the delete branch command handler
   * @param createBranchCommandHandler the create branch command handler
   * @param branchService the branch service
   */
  public BranchController(
      DeleteBranchCommandHandler deleteBranchCommandHandler,
      CreateBranchCommandHandler createBranchCommandHandler,
      BranchService branchService) {
    this.deleteBranchCommandHandler = deleteBranchCommandHandler;
    this.createBranchCommandHandler = createBranchCommandHandler;
    this.branchService = branchService;
  }

  /**
   * List all branches with pagination.
   *
   * @param dataset the dataset name
   * @param limit maximum number of results to return
   * @param offset number of results to skip
   * @return paginated list of branches
   */
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List branches",
      description = "Returns a paginated list of all branches in the dataset with metadata "
          + "including HEAD commit ID, protection status, and timestamps"
  )
  @ApiResponse(
      responseCode = "200",
      description = "Branch list returned successfully",
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
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request - Invalid pagination parameters",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<BranchListResponse> listBranches(
      @Parameter(description = "Dataset name", example = "default", required = true)
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
    BranchListResponse response = branchService.listBranches(dataset, limit, offset);

    // Build headers
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addContentLocation(headers, VersionControlUrls.branches(dataset));

    // Add Link header for next page (RFC 5988)
    if (response.pagination().hasMore()) {
      String nextUrl = buildNextPageUrl(dataset, limit, offset);
      headers.add("Link", String.format("<%s>; rel=\"next\"", nextUrl));
    }

    return ResponseEntity.ok().headers(headers).body(response);
  }

  /**
   * Create a new branch.
   *
   * @param dataset the dataset name
   * @param request the branch creation request
   * @param author the author of the branch creation
   * @return the created branch information
   */
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  @Operation(
      summary = "Create branch",
      description = "Create a new branch from a commit or branch"
  )
  @ApiResponse(
      responseCode = "202",
      description = "Branch creation accepted (eventual consistency)",
      headers = {
          @Header(
              name = "Location",
              description = "URL of the created branch",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "Content-Location",
              description = "Canonical URL for the created branch",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "ETag",
              description = "Head commit ID",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "SPARQL-VC-Status",
              description = "Status of the operation (pending)",
              schema = @Schema(type = "string")
          )
      },
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "400",
      description = "Invalid request",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Source ref not found",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "409",
      description = "Branch already exists",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<?> createBranch(
      @Parameter(description = "Dataset name", example = "default", required = true)
      @PathVariable String dataset,
      @RequestBody CreateBranchRequest request,
      @Parameter(
          description = "Author of the branch creation",
          example = "John Doe <john@example.org>")
      @RequestHeader(name = "SPARQL-VC-Author", required = false) String author
  ) {
    // Validate request
    try {
      request.validate();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest()
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(e.getMessage(), HttpStatus.BAD_REQUEST.value(),
              "INVALID_REQUEST"));
    }

    // Create command
    CreateBranchCommand command = new CreateBranchCommand(
        dataset,
        request.name(),
        request.from(),
        request.getProtectedFlag(),
        author != null ? author : "anonymous"
    );

    // Handle command
    try {
      BranchCreatedEvent event = (BranchCreatedEvent) createBranchCommandHandler.handle(command);

      // Build canonical URL
      String canonicalUrl = VersionControlUrls.branch(dataset, event.branchName());

      // Build headers
      HttpHeaders headers = new HttpHeaders();
      headers.setLocation(java.net.URI.create(canonicalUrl));
      ResponseHeaderBuilder.addContentLocation(headers, canonicalUrl);
      ResponseHeaderBuilder.addCommitLink(headers, dataset, event.commitId());
      headers.setETag("\"" + event.commitId() + "\"");
      headers.set("SPARQL-VC-Status", "pending");
      headers.setContentType(MediaType.APPLICATION_JSON);

      // Build response
      CreateBranchResponse response = new CreateBranchResponse(
          event.branchName(),
          event.commitId(),
          event.sourceRef(),
          event.isProtected()
      );

      return ResponseEntity
          .status(HttpStatus.ACCEPTED)  // 202, not 201 (eventual consistency)
          .headers(headers)
          .body(response);
    } catch (BranchAlreadyExistsException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(e.getMessage(), HttpStatus.CONFLICT.value(),
              "BRANCH_ALREADY_EXISTS"));
    } catch (RefNotFoundException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(e.getMessage(), HttpStatus.NOT_FOUND.value(),
              "REF_NOT_FOUND"));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest()
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(e.getMessage(), HttpStatus.BAD_REQUEST.value(),
              "INVALID_BRANCH_NAME"));
    }
  }

  /**
   * Get branch information.
   *
   * @param dataset the dataset name
   * @param name the branch name
   * @return branch information
   */
  @GetMapping(value = "/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get branch",
      description = "Returns metadata for a specific branch including HEAD commit ID, "
          + "protection status, and timestamps"
  )
  @ApiResponse(
      responseCode = "200",
      description = "Branch details returned successfully",
      headers = {
          @Header(
              name = "ETag",
              description = "Head commit id (strong ETag)",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "Content-Location",
              description = "Canonical URL for this branch",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "Link",
              description = "Link to HEAD commit",
              schema = @Schema(type = "string")
          )
      },
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "404",
      description = "Branch or dataset not found",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<?> getBranch(
      @Parameter(description = "Dataset name", example = "default", required = true)
      @PathVariable String dataset,
      @Parameter(description = "Branch name", example = "main", required = true)
      @PathVariable String name
  ) {
    return branchService.getBranchInfo(dataset, name)
        .<ResponseEntity<?>>map(info -> {
          // Build headers with canonical URL and links
          HttpHeaders headers = new HttpHeaders();
          ResponseHeaderBuilder.addContentLocation(headers,
              VersionControlUrls.branch(dataset, name));
          ResponseHeaderBuilder.addCommitLink(headers, dataset, info.headCommitId());
          headers.setETag("\"" + info.headCommitId() + "\"");

          return ResponseEntity.ok().headers(headers).body(info);
        })
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(new ProblemDetail(
                "Branch not found: " + name,
                HttpStatus.NOT_FOUND.value(),
                "BRANCH_NOT_FOUND"
            )));
  }

  /**
   * Delete a branch.
   *
   * @param dataset dataset name
   * @param name branch name
   * @param author the author of the deletion operation
   * @return no content on success
   */
  @DeleteMapping("/{name}")
  @Operation(
      summary = "Delete branch",
      description = "Deletes a branch. Protected branches cannot be deleted. "
          + "Commits are preserved even after branch deletion."
  )
  @ApiResponse(responseCode = "202", description = "Branch deletion accepted")
  @ApiResponse(
      responseCode = "403",
      description = "Cannot delete protected branch",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Branch not found",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<Void> deleteBranch(
      @Parameter(description = "Dataset name", example = "default", required = true)
      @PathVariable String dataset,
      @Parameter(description = "Branch name", example = "feature-branch", required = true)
      @PathVariable String name,
      @Parameter(
          description = "Author of the deletion operation",
          example = "John Doe <john@example.org>")
      @RequestHeader(value = "X-Author", defaultValue = "anonymous") String author
  ) {
    // Create command
    DeleteBranchCommand command = new DeleteBranchCommand(
        dataset,
        name,
        author
    );

    // Handle command (publishes event asynchronously)
    deleteBranchCommandHandler.handle(command);

    // Build response headers
    HttpHeaders headers = new HttpHeaders();
    headers.set("SPARQL-VC-Status", "pending");

    // Return 202 Accepted (response sent before repository update)
    return ResponseEntity.accepted().headers(headers).build();
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
    return String.format("/%s/version/branches?offset=%d&limit=%d",
        dataset, offset + limit, limit);
  }
}
