package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.chucc.vcserver.command.MergeCommand;
import org.chucc.vcserver.command.MergeCommandHandler;
import org.chucc.vcserver.controller.util.ResponseHeaderBuilder;
import org.chucc.vcserver.controller.util.VersionControlUrls;
import org.chucc.vcserver.dto.MergeRequest;
import org.chucc.vcserver.dto.MergeResponse;
import org.chucc.vcserver.dto.ProblemDetail;
import org.chucc.vcserver.event.BranchMergedEvent;
import org.chucc.vcserver.event.BranchResetEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Merge operations endpoint for version control with conflict resolution strategies.
 */
@RestController
@RequestMapping("/{dataset}/version/merge")
@Tag(name = "Version Control", description = "Merge operations")
public class MergeController {

  private final MergeCommandHandler mergeCommandHandler;

  /**
   * Constructs a MergeController.
   *
   * @param mergeCommandHandler the merge command handler
   */
  public MergeController(MergeCommandHandler mergeCommandHandler) {
    this.mergeCommandHandler = mergeCommandHandler;
  }

  /**
   * Merge two branches or commits with conflict resolution strategies.
   *
   * @param request merge request
   * @param dataset the dataset name
   * @param author the author of the merge
   * @return merge result
   */
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  @Operation(
      summary = "Merge branches/commits",
      description = "Merge two branches or commits with automatic conflict resolution"
  )
  @ApiResponse(
      responseCode = "200",
      description = "Merge result (fast-forward or merge commit created)",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "204",
      description = "No-op merge (already up-to-date)"
  )
  @ApiResponse(
      responseCode = "400",
      description = "Invalid request",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Branch or ref not found",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "409",
      description = "Merge conflict",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "422",
      description = "Fast-forward not possible but fastForward=only",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<?> mergeBranches(
      @Parameter(description = "Dataset name", required = true)
      @PathVariable String dataset,
      @RequestBody MergeRequest request,
      @Parameter(description = "Author of the merge")
      @RequestHeader(name = "SPARQL-VC-Author", required = false) String author
  ) {
    // Validate request
    try {
      request.validate();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest()
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(e.getMessage(), HttpStatus.BAD_REQUEST.value(),
              "invalid_request"));
    }

    // Create command
    MergeCommand command = new MergeCommand(
        dataset,
        request.into(),
        request.from(),
        request.normalizedFastForward(),
        request.normalizedStrategy(),
        request.normalizedConflictScope(),
        author != null ? author : "anonymous",
        null  // no custom message support yet
    );

    // Handle command
    try {
      VersionControlEvent event = mergeCommandHandler.handle(command);

      // Check for no-op merge
      if (event == null) {
        return ResponseEntity.noContent().build();
      }

      // Build response based on event type
      if (event instanceof BranchResetEvent resetEvent) {
        // Fast-forward merge - build headers first
        HttpHeaders headers = new HttpHeaders();
        ResponseHeaderBuilder.addContentLocation(headers,
            VersionControlUrls.branch(dataset, request.into()));
        ResponseHeaderBuilder.addCommitLink(headers, dataset, resetEvent.toCommitId());
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Build response
        MergeResponse response = MergeResponse.fastForward(
            request.into(),
            request.from(),
            resetEvent.toCommitId()
        );

        return ResponseEntity.ok()
            .headers(headers)
            .body(response);

      } else if (event instanceof BranchMergedEvent mergedEvent) {
        // Merge commit created - build headers first
        HttpHeaders headers = new HttpHeaders();
        ResponseHeaderBuilder.addContentLocation(headers,
            VersionControlUrls.branch(dataset, request.into()));
        ResponseHeaderBuilder.addCommitLink(headers, dataset, mergedEvent.commitId());
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Build response
        MergeResponse response = MergeResponse.merged(
            request.into(),
            request.from(),
            mergedEvent.commitId(),
            request.normalizedStrategy(),
            request.normalizedConflictScope(),
            mergedEvent.conflictsResolved() != null ? mergedEvent.conflictsResolved() : 0
        );

        return ResponseEntity.ok()
            .headers(headers)
            .body(response);

      } else {
        throw new IllegalStateException("Unexpected event type: " + event.getClass());
      }

    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(e.getMessage(), HttpStatus.NOT_FOUND.value(),
              "ref_not_found"));
    } catch (IllegalStateException e) {
      // Fast-forward-only mode failure
      return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(e.getMessage(),
              HttpStatus.UNPROCESSABLE_ENTITY.value(),
              "fast_forward_required"));
    }
    // Note: MergeConflictException is handled by VcExceptionHandler (409 Conflict)
  }
}
