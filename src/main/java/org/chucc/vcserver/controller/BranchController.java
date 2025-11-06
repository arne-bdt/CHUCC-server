package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.chucc.vcserver.command.CreateBranchCommand;
import org.chucc.vcserver.command.CreateBranchCommandHandler;
import org.chucc.vcserver.command.DeleteBranchCommand;
import org.chucc.vcserver.command.DeleteBranchCommandHandler;
import org.chucc.vcserver.dto.BranchInfo;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Branch management endpoints for version control.
 */
@RestController
@RequestMapping("/version/branches")
@Tag(name = "Version Control", description = "Branch management operations")
public class BranchController {

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
   * List all branches with full metadata.
   *
   * @param dataset the dataset name
   * @return list of branches
   */
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List branches",
      description = "Returns a list of all branches in the dataset with metadata including "
          + "HEAD commit ID, protection status, and timestamps"
  )
  @ApiResponse(
      responseCode = "200",
      description = "Branch list returned successfully",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  public ResponseEntity<BranchListResponse> listBranches(
      @Parameter(description = "Dataset name", example = "default")
      @RequestParam(defaultValue = "default") String dataset
  ) {
    List<BranchInfo> branches = branchService.listBranches(dataset);
    return ResponseEntity.ok(new BranchListResponse(branches));
  }

  /**
   * Create a new branch.
   *
   * @param request the branch creation request
   * @param dataset the dataset name
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
      @RequestBody CreateBranchRequest request,
      @Parameter(description = "Dataset name", example = "default")
      @RequestParam(defaultValue = "default") String dataset,
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

      // Build response
      CreateBranchResponse response = new CreateBranchResponse(
          event.branchName(),
          event.commitId(),
          event.sourceRef(),
          event.isProtected()
      );

      // Build Location URI
      String location = ServletUriComponentsBuilder
          .fromCurrentRequest()
          .path("/{name}")
          .buildAndExpand(event.branchName())
          .toUriString();

      return ResponseEntity
          .status(HttpStatus.ACCEPTED)  // 202, not 201 (eventual consistency)
          .header(HttpHeaders.LOCATION, location)
          .eTag("\"" + event.commitId() + "\"")
          .header("SPARQL-VC-Status", "pending")
          .contentType(MediaType.APPLICATION_JSON)
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
   * @param name the branch name
   * @param dataset the dataset name
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
      headers = @Header(
          name = "ETag",
          description = "Head commit id (strong ETag)",
          schema = @Schema(type = "string")
      ),
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "404",
      description = "Branch or dataset not found",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<?> getBranch(
      @Parameter(description = "Branch name", example = "main", required = true)
      @PathVariable String name,
      @Parameter(description = "Dataset name", example = "default")
      @RequestParam(defaultValue = "default") String dataset
  ) {
    return branchService.getBranchInfo(dataset, name)
        .<ResponseEntity<?>>map(info -> ResponseEntity.ok()
            .eTag("\"" + info.headCommitId() + "\"")
            .body(info))
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
   * @param name branch name
   * @param dataset dataset name (defaults to "default")
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
      @Parameter(description = "Branch name", example = "feature-branch", required = true)
      @PathVariable String name,
      @Parameter(description = "Dataset name", example = "default")
      @RequestParam(defaultValue = "default") String dataset,
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
}
