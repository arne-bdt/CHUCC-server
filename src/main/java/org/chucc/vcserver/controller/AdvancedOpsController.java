package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.chucc.vcserver.command.CherryPickCommand;
import org.chucc.vcserver.command.CherryPickCommandHandler;
import org.chucc.vcserver.command.RebaseCommand;
import org.chucc.vcserver.command.RebaseCommandHandler;
import org.chucc.vcserver.command.ResetBranchCommand;
import org.chucc.vcserver.command.ResetBranchCommandHandler;
import org.chucc.vcserver.command.RevertCommitCommand;
import org.chucc.vcserver.command.RevertCommitCommandHandler;
import org.chucc.vcserver.dto.CherryPickRequest;
import org.chucc.vcserver.dto.CherryPickResponse;
import org.chucc.vcserver.dto.ProblemDetail;
import org.chucc.vcserver.dto.RebaseRequest;
import org.chucc.vcserver.dto.RebaseResponse;
import org.chucc.vcserver.dto.ResetRequest;
import org.chucc.vcserver.dto.ResetResponse;
import org.chucc.vcserver.dto.RevertRequest;
import org.chucc.vcserver.dto.RevertResponse;
import org.chucc.vcserver.event.BranchRebasedEvent;
import org.chucc.vcserver.event.BranchResetEvent;
import org.chucc.vcserver.event.CherryPickedEvent;
import org.chucc.vcserver.event.RevertCreatedEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

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
  private final CherryPickCommandHandler cherryPickCommandHandler;
  private final RevertCommitCommandHandler revertCommitCommandHandler;
  private final RebaseCommandHandler rebaseCommandHandler;

  /**
   * Constructs an AdvancedOpsController.
   *
   * @param resetBranchCommandHandler the command handler for resetting branches
   * @param cherryPickCommandHandler the command handler for cherry-picking commits
   * @param revertCommitCommandHandler the command handler for reverting commits
   * @param rebaseCommandHandler the command handler for rebasing branches
   */
  public AdvancedOpsController(
      ResetBranchCommandHandler resetBranchCommandHandler,
      CherryPickCommandHandler cherryPickCommandHandler,
      RevertCommitCommandHandler revertCommitCommandHandler,
      RebaseCommandHandler rebaseCommandHandler) {
    this.resetBranchCommandHandler = resetBranchCommandHandler;
    this.cherryPickCommandHandler = cherryPickCommandHandler;
    this.revertCommitCommandHandler = revertCommitCommandHandler;
    this.rebaseCommandHandler = rebaseCommandHandler;
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

  /**
   * Cherry-pick a commit onto a target branch.
   *
   * @param request the cherry-pick request containing commit and target branch
   * @param dataset the dataset name (default: "default")
   * @param author the author of the cherry-pick commit (from SPARQL-VC-Author header)
   * @param message optional commit message (from SPARQL-VC-Message header)
   * @return the cherry-pick response with new commit details
   */
  @PostMapping(
      value = "/cherry-pick",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  @Operation(
      summary = "Cherry-pick commit",
      description = "Apply a specific commit to a target branch"
  )
  @ApiResponse(
      responseCode = "201",
      description = "Commit cherry-picked successfully",
      headers = {
          @Header(
              name = "Location",
              description = "URI of the new commit",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "ETag",
              description = "New commit ID",
              schema = @Schema(type = "string")
          )
      },
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request (invalid request or missing author)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Source commit or target branch not found",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "409",
      description = "Cherry-pick conflict",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<?> cherryPick(
      @RequestBody CherryPickRequest request,
      @Parameter(description = "Dataset name")
      @RequestParam(defaultValue = "default") String dataset,
      @Parameter(description = "Author of the cherry-pick commit")
      @RequestHeader(name = "SPARQL-VC-Author", required = false) String author,
      @Parameter(description = "Optional commit message")
      @RequestHeader(name = "SPARQL-VC-Message", required = false) String message
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

    // Validate author header
    if (author == null || author.isBlank()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              "SPARQL-VC-Author header is required",
              400,
              "MISSING_AUTHOR"));
    }

    // Create command
    CherryPickCommand command = new CherryPickCommand(
        dataset,
        request.getCommit(),
        request.getOnto(),
        message,
        author
    );

    // Handle command
    try {
      CherryPickedEvent event = (CherryPickedEvent) cherryPickCommandHandler.handle(command);

      // Build response
      CherryPickResponse response = new CherryPickResponse(
          event.newCommitId(),
          event.branch(),
          event.sourceCommitId()
      );

      // Build Location URI
      String location = ServletUriComponentsBuilder
          .fromCurrentContextPath()
          .path("/version/commits/{id}")
          .buildAndExpand(event.newCommitId())
          .toUriString();

      return ResponseEntity
          .status(HttpStatus.CREATED)
          .header("Location", location)
          .eTag("\"" + event.newCommitId() + "\"")
          .contentType(MediaType.APPLICATION_JSON)
          .body(response);
    } catch (IllegalArgumentException e) {
      // Source commit or target branch not found
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              e.getMessage(),
              404,
              "NOT_FOUND"));
    }
  }

  /**
   * Revert a commit by creating an inverse commit.
   *
   * @param request the revert request containing commit to revert and target branch
   * @param dataset the dataset name (default: "default")
   * @param author the author of the revert commit (from SPARQL-VC-Author header)
   * @param message optional commit message (from SPARQL-VC-Message header)
   * @return the revert response with new commit details
   */
  @PostMapping(
      value = "/revert",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  @Operation(
      summary = "Revert commit",
      description = "Create inverse commit that undoes changes from a specified commit"
  )
  @ApiResponse(
      responseCode = "201",
      description = "Revert commit created",
      headers = {
          @Header(
              name = "Location",
              description = "URI of the revert commit",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "ETag",
              description = "New revert commit ID",
              schema = @Schema(type = "string")
          )
      },
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request (invalid request or missing author)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Commit or branch not found",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "409",
      description = "Revert would cause conflicts",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<?> revertCommit(
      @RequestBody RevertRequest request,
      @Parameter(description = "Dataset name")
      @RequestParam(defaultValue = "default") String dataset,
      @Parameter(description = "Author of the revert commit")
      @RequestHeader(name = "SPARQL-VC-Author", required = false) String author,
      @Parameter(description = "Optional commit message")
      @RequestHeader(name = "SPARQL-VC-Message", required = false) String message
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

    // Validate author header
    if (author == null || author.isBlank()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              "SPARQL-VC-Author header is required",
              400,
              "MISSING_AUTHOR"));
    }

    // Create command
    RevertCommitCommand command = new RevertCommitCommand(
        dataset,
        request.getBranch(),
        request.getCommit(),
        message,
        author
    );

    // Handle command
    try {
      RevertCreatedEvent event = (RevertCreatedEvent) revertCommitCommandHandler.handle(command);

      // Build response
      RevertResponse response = new RevertResponse(
          event.revertCommitId(),
          request.getBranch(),
          event.revertedCommitId()
      );

      // Build Location URI
      String location = ServletUriComponentsBuilder
          .fromCurrentContextPath()
          .path("/version/commits/{id}")
          .buildAndExpand(event.revertCommitId())
          .toUriString();

      return ResponseEntity
          .status(HttpStatus.CREATED)
          .header("Location", location)
          .eTag("\"" + event.revertCommitId() + "\"")
          .contentType(MediaType.APPLICATION_JSON)
          .body(response);
    } catch (IllegalArgumentException e) {
      // Commit or branch not found
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              e.getMessage(),
              404,
              "NOT_FOUND"));
    }
  }

  /**
   * Rebase a branch onto another reference.
   *
   * @param request the rebase request containing branch, onto ref, and from commit
   * @param dataset the dataset name (default: "default")
   * @param author the author of the rebase operation (from SPARQL-VC-Author header)
   * @return the rebase response with new commit details
   */
  @PostMapping(
      value = "/rebase",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  @Operation(
      summary = "Rebase branch",
      description = "Reapply commits from one branch onto another"
  )
  @ApiResponse(
      responseCode = "200",
      description = "Branch rebased successfully",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request (invalid request or missing author)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Branch or commit not found",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "409",
      description = "Rebase conflict",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<?> rebaseBranch(
      @RequestBody RebaseRequest request,
      @Parameter(description = "Dataset name")
      @RequestParam(defaultValue = "default") String dataset,
      @Parameter(description = "Author of the rebase operation")
      @RequestHeader(name = "SPARQL-VC-Author", required = false) String author
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

    // Validate author header
    if (author == null || author.isBlank()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              "SPARQL-VC-Author header is required",
              400,
              "MISSING_AUTHOR"));
    }

    // Create command
    RebaseCommand command = new RebaseCommand(
        dataset,
        request.getBranch(),
        request.getOnto(),
        request.getFrom(),
        author
    );

    // Handle command
    try {
      BranchRebasedEvent event = (BranchRebasedEvent) rebaseCommandHandler.handle(command);

      // Build response
      RebaseResponse response = new RebaseResponse(
          event.branch(),
          event.newHead(),
          event.newCommits(),
          event.newCommits().size()
      );

      return ResponseEntity
          .ok()
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
