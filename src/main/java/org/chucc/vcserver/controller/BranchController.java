package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.chucc.vcserver.command.DeleteBranchCommand;
import org.chucc.vcserver.command.DeleteBranchCommandHandler;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Branch management endpoints for version control.
 */
@RestController
@RequestMapping("/version/branches")
@Tag(name = "Version Control", description = "Branch management operations")
public class BranchController {

  private final DeleteBranchCommandHandler deleteBranchCommandHandler;

  /**
   * Constructs a BranchController.
   *
   * @param deleteBranchCommandHandler the delete branch command handler
   */
  public BranchController(DeleteBranchCommandHandler deleteBranchCommandHandler) {
    this.deleteBranchCommandHandler = deleteBranchCommandHandler;
  }

  /**
   * List all branches.
   *
   * @return list of branches (501 stub)
   */
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "List branches", description = "List all branches in the repository")
  @ApiResponse(
      responseCode = "200",
      description = "Branch list",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "501",
      description = "Not Implemented",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<String> listBranches() {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body("{\"title\":\"Not Implemented\",\"status\":501}");
  }

  /**
   * Create a new branch.
   *
   * @param branchRequest branch creation request
   * @return created branch (501 stub)
   */
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  @Operation(summary = "Create branch", description = "Create a new branch from a commit or branch")
  @ApiResponse(
      responseCode = "201",
      description = "Branch created",
      headers = @Header(
          name = "Location",
          description = "URL of the created branch",
          schema = @Schema(type = "string")
      ),
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "501",
      description = "Not Implemented",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<String> createBranch(@RequestBody String branchRequest) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body("{\"title\":\"Not Implemented\",\"status\":501}");
  }

  /**
   * Get branch information.
   *
   * @param name branch name
   * @return branch info (501 stub)
   */
  @GetMapping(value = "/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Get branch", description = "Get branch information")
  @ApiResponse(
      responseCode = "200",
      description = "Branch info",
      headers = @Header(
          name = "ETag",
          description = "Head commit id (strong ETag)",
          schema = @Schema(type = "string")
      ),
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "404",
      description = "Branch not found",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "501",
      description = "Not Implemented",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<String> getBranch(
      @Parameter(description = "Branch name", required = true)
      @PathVariable String name
  ) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body("{\"title\":\"Not Implemented\",\"status\":501}");
  }

  /**
   * Delete a branch.
   *
   * @param name branch name
   * @param author the author of the deletion operation
   * @return no content on success
   */
  @DeleteMapping("/{name}")
  @Operation(
      summary = "Delete branch",
      description = "Deletes a branch. The main branch cannot be deleted. "
          + "Commits are preserved even after branch deletion."
  )
  @ApiResponse(responseCode = "204", description = "Branch deleted successfully")
  @ApiResponse(
      responseCode = "403",
      description = "Cannot delete protected branch (main)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Branch not found",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<Void> deleteBranch(
      @Parameter(description = "Branch name", required = true)
      @PathVariable String name,
      @Parameter(description = "Author of the deletion operation")
      @RequestHeader(value = "X-Author", defaultValue = "anonymous") String author
  ) {
    // Create command
    DeleteBranchCommand command = new DeleteBranchCommand(
        "default",  // TODO: Get from request context
        name,
        author
    );

    // Handle command (publishes event asynchronously)
    deleteBranchCommandHandler.handle(command);

    // Return 204 No Content (response sent before repository update)
    return ResponseEntity.noContent().build();
  }
}
