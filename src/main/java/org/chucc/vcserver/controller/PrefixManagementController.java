package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.jena.sparql.core.DatasetGraph;
import org.chucc.vcserver.command.UpdatePrefixesCommand;
import org.chucc.vcserver.command.UpdatePrefixesCommandHandler;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.dto.CommitResponse;
import org.chucc.vcserver.dto.PrefixResponse;
import org.chucc.vcserver.dto.UpdatePrefixesRequest;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Prefix Management Protocol (PMP).
 *
 * <p>Implements version-aware prefix management with commit creation.
 * All prefix modifications create commits, making changes auditable and
 * enabling time-travel queries.
 *
 * @see <a href="../../protocol/Prefix_Management_Protocol.md">PMP Specification</a>
 */
@RestController
@RequestMapping("/version/datasets/{dataset}")
@Tag(name = "Prefix Management", description = "Manage namespace prefixes with version control")
public class PrefixManagementController {

  private final MaterializedBranchRepository materializedBranchRepository;
  private final BranchRepository branchRepository;
  private final UpdatePrefixesCommandHandler updatePrefixesCommandHandler;

  /**
   * Creates a prefix management controller.
   *
   * @param materializedBranchRepository the materialized branch repository
   * @param branchRepository the branch repository
   * @param updatePrefixesCommandHandler the prefix update handler
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories and handlers are Spring-managed beans "
          + "and are intentionally shared")
  public PrefixManagementController(
      MaterializedBranchRepository materializedBranchRepository,
      BranchRepository branchRepository,
      UpdatePrefixesCommandHandler updatePrefixesCommandHandler) {
    this.materializedBranchRepository = materializedBranchRepository;
    this.branchRepository = branchRepository;
    this.updatePrefixesCommandHandler = updatePrefixesCommandHandler;
  }

  /**
   * Creates a response for a no-op prefix operation.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param author the author
   * @return ResponseEntity with 200 OK and no-op message
   */
  private ResponseEntity<CommitResponse> createNoOpResponse(
      String dataset, String branch, String author) {
    Branch branchObj = branchRepository
        .findByDatasetAndName(dataset, branch)
        .orElseThrow(() -> new BranchNotFoundException(branch));

    CommitResponse response = new CommitResponse(
        branchObj.getCommitId().value(),
        List.of(),  // No parents since no new commit created
        author,
        "No changes made (prefix map already matches requested state)",
        DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now())
    );

    return ResponseEntity
        .ok()
        .eTag("\"" + branchObj.getCommitId().value() + "\"")
        .body(response);
  }

  /**
   * Executes prefix update command and builds HTTP response.
   *
   * @param cmd the prefix update command
   * @param dataset the dataset name
   * @param branch the branch name
   * @param author the author
   * @return ResponseEntity with 201 Created or 200 OK (no-op)
   */
  private ResponseEntity<CommitResponse> executeAndBuildResponse(
      UpdatePrefixesCommand cmd,
      String dataset,
      String branch,
      String author) {

    CommitCreatedEvent event = updatePrefixesCommandHandler.handle(cmd);

    // Handle no-op case (patch made no changes)
    if (event == null) {
      return createNoOpResponse(dataset, branch, author);
    }

    URI location = URI.create(
        "/version/datasets/" + dataset + "/commits/" + event.commitId()
    );

    CommitResponse response = new CommitResponse(
        event.commitId(),
        event.parents(),
        event.author(),
        event.message(),
        DateTimeFormatter.ISO_INSTANT.format(event.timestamp())
    );

    return ResponseEntity
        .created(location)
        .eTag("\"" + event.commitId() + "\"")
        .body(response);
  }

  /**
   * Retrieves prefix mappings for a branch.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @return prefix response with mappings and metadata
   * @throws BranchNotFoundException if branch doesn't exist
   */
  @GetMapping("/branches/{branch}/prefixes")
  @Operation(
      summary = "Get prefix mappings",
      description = "Retrieve current prefix mappings for a branch"
  )
  @ApiResponse(
      responseCode = "200",
      description = "Success",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "404",
      description = "Branch not found",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<PrefixResponse> getCurrentPrefixes(
      @Parameter(description = "Dataset name") @PathVariable String dataset,
      @Parameter(description = "Branch name") @PathVariable String branch) {

    // Get branch (validate exists)
    Branch branchObj = branchRepository
        .findByDatasetAndName(dataset, branch)
        .orElseThrow(() -> new BranchNotFoundException(branch));

    // Read prefixes from materialized branch
    DatasetGraph dsg = materializedBranchRepository
        .getBranchGraph(dataset, branch);
    Map<String, String> prefixes = dsg.getDefaultGraph()
        .getPrefixMapping()
        .getNsPrefixMap();

    PrefixResponse response = new PrefixResponse(
        dataset,
        branch,
        branchObj.getCommitId().value(),
        prefixes
    );

    return ResponseEntity
        .ok()
        .eTag("\"" + branchObj.getCommitId().value() + "\"")
        .body(response);
  }

  /**
   * Replaces entire prefix map (creates commit).
   *
   * <p>All existing prefixes are removed, then new prefixes are added.
   * This operation generates RDFPatch with PD (delete old) + PA (add new) directives.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param author the commit author (from SPARQL-VC-Author header)
   * @param request the update request with prefixes and optional message
   * @return commit response with metadata
   */
  @PutMapping("/branches/{branch}/prefixes")
  @Operation(
      summary = "Replace all prefixes",
      description = "Replace entire prefix map (creates commit with PD+PA directives)"
  )
  @ApiResponse(
      responseCode = "201",
      description = "Created",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request (missing author or invalid prefixes)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Branch not found",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<CommitResponse> replacePrefixes(
      @Parameter(description = "Dataset name") @PathVariable String dataset,
      @Parameter(description = "Branch name") @PathVariable String branch,
      @Parameter(description = "Commit author", required = true)
      @RequestHeader("SPARQL-VC-Author") String author,
      @Valid @RequestBody UpdatePrefixesRequest request) {

    UpdatePrefixesCommand cmd = new UpdatePrefixesCommand(
        dataset,
        branch,
        author,
        request.prefixes(),
        UpdatePrefixesCommand.Operation.PUT,
        Optional.ofNullable(request.message())
    );

    return executeAndBuildResponse(cmd, dataset, branch, author);
  }

  /**
   * Adds or updates selected prefixes (creates commit).
   *
   * <p>Only specified prefixes are added/updated. Existing prefixes are preserved.
   * This operation generates RDFPatch with PA (add) directives only.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param author the commit author (from SPARQL-VC-Author header)
   * @param request the update request with prefixes and optional message
   * @return commit response with metadata
   */
  @PatchMapping("/branches/{branch}/prefixes")
  @Operation(
      summary = "Add/update prefixes",
      description = "Add or update selected prefixes (creates commit with PA directives)"
  )
  @ApiResponse(
      responseCode = "201",
      description = "Created",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request (missing author or invalid prefixes)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Branch not found",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<CommitResponse> updatePrefixes(
      @Parameter(description = "Dataset name") @PathVariable String dataset,
      @Parameter(description = "Branch name") @PathVariable String branch,
      @Parameter(description = "Commit author", required = true)
      @RequestHeader("SPARQL-VC-Author") String author,
      @Valid @RequestBody UpdatePrefixesRequest request) {

    UpdatePrefixesCommand cmd = new UpdatePrefixesCommand(
        dataset,
        branch,
        author,
        request.prefixes(),
        UpdatePrefixesCommand.Operation.PATCH,
        Optional.ofNullable(request.message())
    );

    return executeAndBuildResponse(cmd, dataset, branch, author);
  }

  /**
   * Removes specified prefixes (creates commit).
   *
   * <p>Prefixes specified in the 'prefix' query parameters are removed.
   * This operation generates RDFPatch with PD (delete) directives.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param prefixNames the prefix names to remove (query param, can specify multiple)
   * @param message optional commit message (query param)
   * @param author the commit author (from SPARQL-VC-Author header)
   * @return commit response with metadata
   */
  @DeleteMapping("/branches/{branch}/prefixes")
  @Operation(
      summary = "Remove prefixes",
      description = "Remove specified prefixes (creates commit with PD directives)"
  )
  @ApiResponse(
      responseCode = "201",
      description = "Created",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request (missing author or prefix parameter)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Branch not found",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<CommitResponse> deletePrefixes(
      @Parameter(description = "Dataset name") @PathVariable String dataset,
      @Parameter(description = "Branch name") @PathVariable String branch,
      @Parameter(description = "Prefix names to remove", required = true)
      @RequestParam("prefix") List<String> prefixNames,
      @Parameter(description = "Optional commit message")
      @RequestParam(required = false) String message,
      @Parameter(description = "Commit author", required = true)
      @RequestHeader("SPARQL-VC-Author") String author) {

    // Convert prefix list to map (value doesn't matter for DELETE)
    Map<String, String> prefixesToDelete = prefixNames.stream()
        .collect(java.util.stream.Collectors.toMap(p -> p, p -> ""));

    UpdatePrefixesCommand cmd = new UpdatePrefixesCommand(
        dataset,
        branch,
        author,
        prefixesToDelete,
        UpdatePrefixesCommand.Operation.DELETE,
        Optional.ofNullable(message)
    );

    return executeAndBuildResponse(cmd, dataset, branch, author);
  }
}
