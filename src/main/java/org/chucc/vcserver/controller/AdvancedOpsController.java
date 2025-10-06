package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.chucc.vcserver.command.ResetBranchCommand;
import org.chucc.vcserver.command.ResetBranchCommandHandler;
import org.chucc.vcserver.dto.ProblemDetail;
import org.chucc.vcserver.dto.ResetRequest;
import org.chucc.vcserver.dto.ResetResponse;
import org.chucc.vcserver.event.BranchResetEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Advanced version control operations controller.
 * Handles operations like reset, revert, cherry-pick, rebase, and squash.
 * Per SPARQL 1.2 Protocol §3.4.
 */
@RestController
@RequestMapping("/version")
@Tag(name = "Version Control", description = "Advanced version control operations")
public class AdvancedOpsController {

  private final ResetBranchCommandHandler resetBranchCommandHandler;

  /**
   * Constructs an AdvancedOpsController.
   *
   * @param resetBranchCommandHandler the command handler for resetting branches
   */
  public AdvancedOpsController(ResetBranchCommandHandler resetBranchCommandHandler) {
    this.resetBranchCommandHandler = resetBranchCommandHandler;
  }

  /**
   * Reset a branch pointer to a different commit.
   * Supports hard, soft, and mixed reset modes.
   *
   * @param request the reset request containing branch, target commit, and mode
   * @param dataset the dataset name (default: "default")
   * @return the reset response with previous and new head commit IDs
   */
  @PostMapping(
      value = "/reset",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  @Operation(
      summary = "Reset branch pointer",
      description = "Move branch pointer to a different commit (hard/soft/mixed mode)"
  )
  @ApiResponse(
      responseCode = "200",
      description = "Branch reset successfully",
      headers = @Header(
          name = "ETag",
          description = "New head commit ID",
          schema = @Schema(type = "string")
      ),
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request (invalid request)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Branch or commit not found",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<?> resetBranch(
      @RequestBody ResetRequest request,
      @Parameter(description = "Dataset name")
      @RequestParam(defaultValue = "default") String dataset
  ) {
    // Validate request
    try {
      request.validate();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              e.getMessage(),
              400,
              "INVALID_REQUEST"));
    }

    // Create command
    ResetBranchCommand command = new ResetBranchCommand(
        dataset,
        request.getBranch(),
        request.getTo()
    );

    // Handle command
    try {
      BranchResetEvent event = (BranchResetEvent) resetBranchCommandHandler.handle(command);

      // Build response
      ResetResponse response = new ResetResponse(
          request.getBranch(),
          event.toCommitId(),
          event.fromCommitId()
      );

      return ResponseEntity
          .ok()
          .eTag("\"" + event.toCommitId() + "\"")
          .contentType(MediaType.APPLICATION_JSON)
          .body(response);
    } catch (IllegalArgumentException e) {
      // Branch or commit not found
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              e.getMessage(),
              404,
              "NOT_FOUND"));
    }
  }
}
