package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.apache.jena.rdfpatch.RDFPatch;
import org.chucc.vcserver.command.CreateCommitCommand;
import org.chucc.vcserver.command.CreateCommitCommandHandler;
import org.chucc.vcserver.dto.BatchWriteRequest;
import org.chucc.vcserver.dto.BatchWriteResponse;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.service.BatchOperationService;
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
 * Batch update operations endpoint for version control.
 *
 * <p>Combines multiple write operations (SPARQL updates or RDF patches) into a single commit.
 * This is useful for bulk operations and data migrations where you want cleaner history.
 */
@RestController
@RequestMapping("/{dataset}/version/batch")
@Tag(name = "Version Control", description = "Batch update operations")
public class BatchController {

  private final BatchOperationService batchOperationService;
  private final CreateCommitCommandHandler createCommitCommandHandler;

  /**
   * Constructs a BatchController.
   *
   * @param batchOperationService the service for combining operations
   * @param createCommitCommandHandler the handler for creating commits
   */
  public BatchController(
      BatchOperationService batchOperationService,
      CreateCommitCommandHandler createCommitCommandHandler) {
    this.batchOperationService = batchOperationService;
    this.createCommitCommandHandler = createCommitCommandHandler;
  }

  /**
   * Apply a batch of write operations atomically as a single commit.
   *
   * <p>All operations are converted to RDF patches and combined into one patch,
   * which is then committed to the target branch. This creates a single commit
   * instead of multiple commits (cleaner history).
   *
   * @param dataset Dataset name (required)
   * @param author Commit author from header (required)
   * @param message Commit message from header (required)
   * @param request Batch request containing operations and target branch
   * @return 202 Accepted with commit information (CQRS async pattern)
   */
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  @Operation(
      summary = "Apply batch of write operations",
      description = "Combines multiple SPARQL updates or RDF patches into a single commit. "
          + "All operations must target the same branch."
  )
  @ApiResponse(
      responseCode = "202",
      description = "Accepted (commit creation in progress)",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request (validation error, mixed branches, invalid syntax)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Not Found (branch or dataset not found)",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<BatchWriteResponse> executeBatch(
      @PathVariable String dataset,
      @RequestHeader(value = "SPARQL-VC-Author", required = true) String author,
      @RequestHeader(value = "SPARQL-VC-Message", required = true) String message,
      @RequestBody @Valid BatchWriteRequest request) {

    try {
      // Validate request
      request.validate();

      String targetBranch = request.determineTargetBranch();

      // Combine operations into single patch
      RDFPatch combinedPatch = batchOperationService.combineOperations(
          dataset, request.operations(), targetBranch);

      // Serialize patch to string
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      org.apache.jena.rdfpatch.RDFPatchOps.write(out, combinedPatch);
      String patchString = new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);

      // Create single commit using existing command handler
      CreateCommitCommand command = new CreateCommitCommand(
          dataset,
          targetBranch,
          null,  // baseCommitId (use branch HEAD)
          null,  // sparqlUpdate (not used, we have patch)
          patchString,  // patch
          message,
          author,
          Map.of()  // metadata (empty)
      );

      CommitCreatedEvent event =
          (CommitCreatedEvent) createCommitCommandHandler.handle(command);

      // Build response
      BatchWriteResponse response = BatchWriteResponse.accepted(
          event.commitId(),
          targetBranch,
          request.operations().size(),
          message
      );

      return ResponseEntity
          .status(HttpStatus.ACCEPTED)
          .header("Location", "/" + dataset + "/version/commits/" + event.commitId())
          .header("SPARQL-VC-Status", "pending")
          .body(response);

    } catch (IllegalArgumentException e) {
      // Validation error - let exception handler convert to RFC 7807
      throw e;
    }
  }
}
