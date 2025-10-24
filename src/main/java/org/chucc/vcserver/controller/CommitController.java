package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.chucc.vcserver.command.CreateCommitCommand;
import org.chucc.vcserver.command.CreateCommitCommandHandler;
import org.chucc.vcserver.dto.CommitResponse;
import org.chucc.vcserver.dto.ProblemDetail;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.service.PreconditionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Commit introspection and creation endpoints for version control.
 */
@RestController
@RequestMapping("/version/commits")
@Tag(name = "Version Control", description = "Commit introspection and creation operations")
public class CommitController {

  private final CreateCommitCommandHandler createCommitCommandHandler;
  private final PreconditionService preconditionService;
  private final org.chucc.vcserver.service.SelectorResolutionService selectorResolutionService;

  /**
   * Constructs a CommitController.
   *
   * @param createCommitCommandHandler the command handler for creating commits
   * @param preconditionService the service for checking If-Match preconditions
   * @param selectorResolutionService the service for resolving selectors
   */
  public CommitController(
      CreateCommitCommandHandler createCommitCommandHandler,
      PreconditionService preconditionService,
      org.chucc.vcserver.service.SelectorResolutionService selectorResolutionService) {
    this.createCommitCommandHandler = createCommitCommandHandler;
    this.preconditionService = preconditionService;
    this.selectorResolutionService = selectorResolutionService;
  }

  /**
   * Create a new commit by applying an RDF Patch.
   *
   * @param patchBody the RDF Patch content
   * @param branch target branch selector
   * @param commit target commit selector (for detached commits)
   * @param asOf timestamp selector (only with branch)
   * @param dataset dataset name (default: "default")
   * @param author commit author from header
   * @param message commit message from header
   * @param ifMatch ETag for optimistic concurrency control
   * @return 201 Created with commit metadata, or 204 No Content for no-op patches
   */
  @PostMapping(
      consumes = "text/rdf-patch",
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  @Operation(
      summary = "Create commit",
      description = "Create a new commit by applying an RDF Patch to a branch or commit"
  )
  @ApiResponse(
      responseCode = "201",
      description = "Commit created",
      headers = {
          @Header(
              name = "Location",
              description = "URI of the created commit",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "ETag",
              description = "Commit id (strong)",
              schema = @Schema(type = "string")
          )
      },
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "204",
      description = "No Content (no-op patch)",
      content = @Content
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request (invalid selector combination)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "412",
      description = "Precondition Failed (If-Match ETag mismatch)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "415",
      description = "Unsupported Media Type",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<?> createCommit(
      @RequestBody String patchBody,
      @Parameter(description = "Target branch for commit")
      @RequestParam(required = false) String branch,
      @Parameter(description = "Target commit for detached commit")
      @RequestParam(required = false) String commit,
      @Parameter(description = "Timestamp selector (only with branch)")
      @RequestParam(required = false) String asOf,
      @Parameter(description = "Dataset name")
      @RequestParam(defaultValue = "default") String dataset,
      @Parameter(description = "Commit author")
      @RequestHeader(name = "SPARQL-VC-Author", required = false) String author,
      @Parameter(description = "Commit message")
      @RequestHeader(name = "SPARQL-VC-Message", required = false) String message,
      @Parameter(description = "ETag for optimistic concurrency control")
      @RequestHeader(name = "If-Match", required = false) String ifMatch
  ) {
    // Validate selectors
    if (branch == null && commit == null) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              "Invalid selector",
              HttpStatus.BAD_REQUEST.value(),
              "SELECTOR_REQUIRED"));
    }

    if (branch != null && commit != null) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              "Cannot provide both 'branch' and 'commit' selectors",
              HttpStatus.BAD_REQUEST.value(),
              "SELECTOR_CONFLICT"));
    }

    if (asOf != null && commit != null) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              "'asOf' selector can only be used with 'branch', not 'commit'",
              HttpStatus.BAD_REQUEST.value(),
              "INVALID_SELECTOR_COMBINATION"));
    }

    // asOf requires branch per spec §3.2
    if (asOf != null && branch == null) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              "'asOf' selector requires 'branch' parameter",
              HttpStatus.BAD_REQUEST.value(),
              "INVALID_SELECTOR_COMBINATION"));
    }

    // Resolve selectors to base commit ID
    org.chucc.vcserver.domain.CommitId baseCommitId = null;
    if (asOf != null || commit != null) {
      baseCommitId = selectorResolutionService.resolve(dataset, branch, commit, asOf);
    }

    // Check If-Match precondition if branch selector is used
    if (branch != null && ifMatch != null) {
      preconditionService.checkIfMatch(dataset, branch, ifMatch);
    }

    // Set default values if not provided
    String effectiveAuthor = author != null ? author : "anonymous";
    String effectiveMessage = message != null ? message : "";

    // Create command with patch
    CreateCommitCommand command = new CreateCommitCommand(
        dataset,
        branch != null ? branch : "detached-" + commit,
        baseCommitId != null ? baseCommitId.value() : null,
        null,  // sparqlUpdate
        patchBody,  // patch
        effectiveMessage,
        effectiveAuthor,
        Map.of()
    );

    // Handle the command
    CommitCreatedEvent event = (CommitCreatedEvent) createCommitCommandHandler.handle(command);

    // Check for no-op patch
    if (event == null) {
      return ResponseEntity.noContent().build();
    }

    // Build response
    CommitResponse response = new CommitResponse(
        event.commitId(),
        event.parents(),
        event.author(),
        event.message(),
        DateTimeFormatter.ISO_INSTANT.format(event.timestamp())
    );

    return ResponseEntity
        .accepted()
        .location(URI.create("/version/commits/" + event.commitId()))
        .eTag("\"" + event.commitId() + "\"")
        .header("SPARQL-VC-Status", "pending")
        .contentType(MediaType.APPLICATION_JSON)
        .body(response);
  }

  /**
   * Get commit metadata.
   *
   * @param id commit id
   * @return commit metadata (501 stub)
   */
  @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Get commit metadata", description = "Retrieve commit metadata")
  @ApiResponse(
      responseCode = "200",
      description = "Commit metadata",
      headers = @Header(
          name = "ETag",
          description = "Commit id (strong)",
          schema = @Schema(type = "string")
      ),
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
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
  public ResponseEntity<String> getCommit(
      @Parameter(description = "Commit id (UUIDv7)", required = true)
      @PathVariable String id
  ) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body("{\"title\":\"Not Implemented\",\"status\":501}");
  }

}
