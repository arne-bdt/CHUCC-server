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
import org.chucc.vcserver.dto.PrefixSuggestion;
import org.chucc.vcserver.dto.SuggestedPrefixesResponse;
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
 * <p><strong>Note:</strong> OpenAPI annotations for PUT/PATCH/DELETE methods contain
 * semantic duplication (all mutation operations return similar response codes: 200, 201, 400,
 * 403, 404). This duplication is intentional and improves API documentation clarity.
 * Each method's annotations reflect its specific behavior (e.g., PUT returns 200 for no-op,
 * DELETE accepts multiple query params). PMD CPD suppressed for OpenAPI boilerplate.
 *
 * @see <a href="../../protocol/Prefix_Management_Protocol.md">PMP Specification</a>
 */
@RestController
@RequestMapping("/version/datasets/{dataset}")
@Tag(name = "Prefix Management", description = "Manage namespace prefixes with version control")
@SuppressWarnings("CPD-START")  // OpenAPI annotations naturally duplicate across mutation endpoints
public class PrefixManagementController {

  private final MaterializedBranchRepository materializedBranchRepository;
  private final BranchRepository branchRepository;
  private final UpdatePrefixesCommandHandler updatePrefixesCommandHandler;
  private final org.chucc.vcserver.service.DatasetService datasetService;
  private final org.chucc.vcserver.service.PrefixSuggestionService prefixSuggestionService;

  /**
   * Creates a prefix management controller.
   *
   * @param materializedBranchRepository the materialized branch repository
   * @param branchRepository the branch repository
   * @param updatePrefixesCommandHandler the prefix update handler
   * @param datasetService the dataset service for time-travel queries
   * @param prefixSuggestionService the prefix suggestion service
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories and handlers are Spring-managed beans "
          + "and are intentionally shared")
  public PrefixManagementController(
      MaterializedBranchRepository materializedBranchRepository,
      BranchRepository branchRepository,
      UpdatePrefixesCommandHandler updatePrefixesCommandHandler,
      org.chucc.vcserver.service.DatasetService datasetService,
      org.chucc.vcserver.service.PrefixSuggestionService prefixSuggestionService) {
    this.materializedBranchRepository = materializedBranchRepository;
    this.branchRepository = branchRepository;
    this.updatePrefixesCommandHandler = updatePrefixesCommandHandler;
    this.datasetService = datasetService;
    this.prefixSuggestionService = prefixSuggestionService;
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
      description = "Retrieve current prefix mappings for a branch. "
          + "Returns empty map if no prefixes defined."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Prefix map retrieved successfully",
      content = @Content(
          mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @io.swagger.v3.oas.annotations.media.Schema(
              implementation = PrefixResponse.class
          )
      ),
      headers = @io.swagger.v3.oas.annotations.headers.Header(
          name = "ETag",
          description = "Commit ID of the current branch HEAD",
          schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")
      )
  )
  @ApiResponse(
      responseCode = "404",
      description = "Branch or dataset not found",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<PrefixResponse> getCurrentPrefixes(
      @Parameter(description = "Dataset name", example = "mydata") @PathVariable String dataset,
      @Parameter(description = "Branch name", example = "main") @PathVariable String branch) {

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
      description = "Replace entire prefix map (creates commit with PD+PA directives). "
          + "All existing prefixes are removed, then new prefixes are added. "
          + "Returns 200 OK if no changes needed (prefix map already matches request)."
  )
  @ApiResponse(
      responseCode = "201",
      description = "Commit created successfully",
      content = @Content(
          mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @io.swagger.v3.oas.annotations.media.Schema(
              implementation = CommitResponse.class
          )
      ),
      headers = {
          @io.swagger.v3.oas.annotations.headers.Header(
              name = "Location",
              description = "URL of the created commit",
              schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")
          ),
          @io.swagger.v3.oas.annotations.headers.Header(
              name = "ETag",
              description = "Commit ID as entity tag",
              schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")
          )
      }
  )
  @ApiResponse(
      responseCode = "200",
      description = "No changes made (prefix map already matches requested state)",
      content = @Content(
          mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @io.swagger.v3.oas.annotations.media.Schema(
              implementation = CommitResponse.class
          )
      )
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request (missing author header, invalid prefix name, or relative IRI)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "403",
      description = "Forbidden (branch is protected)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Branch or dataset not found",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<CommitResponse> replacePrefixes(
      @Parameter(description = "Dataset name", example = "mydata") @PathVariable String dataset,
      @Parameter(description = "Branch name", example = "main") @PathVariable String branch,
      @Parameter(
          description = "Commit author (required)",
          required = true,
          example = "Alice <alice@example.org>")
      @RequestHeader("SPARQL-VC-Author") String author,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          description = "New prefix map and optional commit message",
          required = true,
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @io.swagger.v3.oas.annotations.media.Schema(
                  implementation = UpdatePrefixesRequest.class
              )
          )
      )
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
      description = "Add or update selected prefixes (creates commit with PA directives). "
          + "Only specified prefixes are affected; existing prefixes are preserved. "
          + "Returns 200 OK if no changes needed (prefixes already exist with same values)."
  )
  @ApiResponse(
      responseCode = "201",
      description = "Commit created successfully",
      content = @Content(
          mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @io.swagger.v3.oas.annotations.media.Schema(
              implementation = CommitResponse.class
          )
      ),
      headers = {
          @io.swagger.v3.oas.annotations.headers.Header(
              name = "Location",
              description = "URL of the created commit",
              schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")
          ),
          @io.swagger.v3.oas.annotations.headers.Header(
              name = "ETag",
              description = "Commit ID as entity tag",
              schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")
          )
      }
  )
  @ApiResponse(
      responseCode = "200",
      description = "No changes made (prefixes already exist with requested values)",
      content = @Content(
          mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @io.swagger.v3.oas.annotations.media.Schema(
              implementation = CommitResponse.class
          )
      )
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request (missing author header, invalid prefix name, or relative IRI)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "403",
      description = "Forbidden (branch is protected)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Branch or dataset not found",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<CommitResponse> updatePrefixes(
      @Parameter(description = "Dataset name", example = "mydata") @PathVariable String dataset,
      @Parameter(description = "Branch name", example = "main") @PathVariable String branch,
      @Parameter(
          description = "Commit author (required)",
          required = true,
          example = "Alice <alice@example.org>")
      @RequestHeader("SPARQL-VC-Author") String author,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          description = "Prefixes to add/update and optional commit message",
          required = true,
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @io.swagger.v3.oas.annotations.media.Schema(
                  implementation = UpdatePrefixesRequest.class
              )
          )
      )
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
      description = "Remove specified prefixes (creates commit with PD directives). "
          + "Multiple prefixes can be specified using repeated 'prefix' query parameters. "
          + "Returns 200 OK if no changes needed (prefixes don't exist)."
  )
  @ApiResponse(
      responseCode = "201",
      description = "Commit created successfully",
      content = @Content(
          mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @io.swagger.v3.oas.annotations.media.Schema(
              implementation = CommitResponse.class
          )
      ),
      headers = {
          @io.swagger.v3.oas.annotations.headers.Header(
              name = "Location",
              description = "URL of the created commit",
              schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")
          ),
          @io.swagger.v3.oas.annotations.headers.Header(
              name = "ETag",
              description = "Commit ID as entity tag",
              schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")
          )
      }
  )
  @ApiResponse(
      responseCode = "200",
      description = "No changes made (prefixes don't exist)",
      content = @Content(
          mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @io.swagger.v3.oas.annotations.media.Schema(
              implementation = CommitResponse.class
          )
      )
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request (missing author header or prefix parameter)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "403",
      description = "Forbidden (branch is protected)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Branch or dataset not found",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<CommitResponse> deletePrefixes(
      @Parameter(description = "Dataset name", example = "mydata") @PathVariable String dataset,
      @Parameter(description = "Branch name", example = "main") @PathVariable String branch,
      @Parameter(
          description = "Prefix names to remove (can be repeated: ?prefix=foo&prefix=bar)",
          required = true,
          example = "temp"
      )
      @RequestParam("prefix") List<String> prefixNames,
      @Parameter(
          description = "Optional commit message",
          example = "Remove temporary prefixes")
      @RequestParam(required = false) String message,
      @Parameter(
          description = "Commit author (required)",
          required = true,
          example = "Alice <alice@example.org>")
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

  /**
   * Retrieves prefix mappings at a specific commit (time-travel query).
   *
   * <p>This endpoint allows querying historical prefix states by materializing
   * the dataset at the specified commit. The operation leverages caching for
   * performance (LRU cache with max 100 entries).</p>
   *
   * <p><strong>Caching Behavior:</strong></p>
   * <ul>
   *   <li>Cached commits: ~10ms response time (in-memory lookup)</li>
   *   <li>Uncached commits: ~1s typical (on-demand materialization)</li>
   *   <li>Cache shared with other time-travel operations</li>
   * </ul>
   *
   * <p><strong>Response Characteristics:</strong></p>
   * <ul>
   *   <li>No {@code branch} field (this is a commit query, not branch query)</li>
   *   <li>ETag set to commit ID (commits are immutable)</li>
   *   <li>Works for any commit (cached or not)</li>
   * </ul>
   *
   * @param dataset the dataset name
   * @param commitId the commit ID (UUIDv7 format)
   * @return prefix response with no branch field
   * @throws org.chucc.vcserver.exception.DatasetNotFoundException if dataset doesn't exist
   * @throws org.chucc.vcserver.exception.CommitNotFoundException if commit doesn't exist
   */
  @Operation(
      summary = "Get prefixes at specific commit",
      description = "Retrieves historical prefix mappings by time-traveling to a specific commit. "
          + "Response does not include branch field since this is a commit-based query."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Prefixes retrieved successfully",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE),
      headers = @io.swagger.v3.oas.annotations.headers.Header(
          name = "ETag",
          description = "Commit ID as entity tag"
      )
  )
  @ApiResponse(
      responseCode = "404",
      description = "Dataset or commit not found",
      content = @Content(mediaType = "application/problem+json")
  )
  @GetMapping("/commits/{commitId}/prefixes")
  public ResponseEntity<PrefixResponse> getPrefixesAtCommit(
      @Parameter(description = "Dataset name", example = "mydata")
      @PathVariable String dataset,
      @Parameter(description = "Commit ID (UUIDv7)", example = "01JCDN2XYZ...")
      @PathVariable String commitId) {

    try {
      // Parse and validate commit ID
      org.chucc.vcserver.domain.CommitId commitIdObj =
          org.chucc.vcserver.domain.CommitId.of(commitId);

      // Materialize dataset at specific commit
      org.apache.jena.query.Dataset materializedDataset =
          datasetService.materializeAtCommit(dataset, commitIdObj);

      // Extract prefixes from default graph
      Map<String, String> prefixes = materializedDataset.asDatasetGraph()
          .getDefaultGraph()
          .getPrefixMapping()
          .getNsPrefixMap();

      PrefixResponse response = new PrefixResponse(
          dataset,
          null,  // No branch (commit query)
          commitId,
          prefixes
      );

      return ResponseEntity
          .ok()
          .eTag("\"" + commitId + "\"")
          .body(response);

    } catch (org.chucc.vcserver.exception.DatasetNotFoundException e) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND,
          "Dataset not found: " + dataset,
          e
      );
    } catch (org.chucc.vcserver.exception.CommitNotFoundException e) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND,
          "Commit not found: " + commitId,
          e
      );
    }
  }

  /**
   * Suggests conventional prefix mappings for a branch.
   *
   * <p>Analyzes the dataset to discover common namespace patterns and suggests
   * conventional prefixes based on the prefix.cc database. Suggestions are sorted
   * by usage frequency (descending) and marked as either SUGGESTED or ALREADY_DEFINED.
   *
   * <p><strong>Use Cases:</strong></p>
   * <ul>
   *   <li>After importing RDF/XML with full URIs (no prefixes)</li>
   *   <li>Discovering ontologies used in the dataset</li>
   *   <li>Standardizing prefix names across team</li>
   * </ul>
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @return suggested prefixes response with suggestions sorted by frequency
   * @throws BranchNotFoundException if branch doesn't exist
   */
  @GetMapping("/branches/{branch}/prefixes/suggested")
  @Operation(
      summary = "Suggest prefix mappings",
      description = "Analyzes dataset to discover namespaces and suggest conventional prefixes "
          + "based on the prefix.cc database. Useful after importing RDF/XML with full URIs "
          + "or discovering new ontologies. Suggestions are sorted by usage frequency (descending)."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Suggestions retrieved successfully (empty list if no namespaces detected)",
      content = @Content(
          mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @io.swagger.v3.oas.annotations.media.Schema(
              implementation = SuggestedPrefixesResponse.class
          )
      )
  )
  @ApiResponse(
      responseCode = "404",
      description = "Branch or dataset not found",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<SuggestedPrefixesResponse> suggestPrefixes(
      @Parameter(description = "Dataset name", example = "mydata") @PathVariable String dataset,
      @Parameter(description = "Branch name", example = "main") @PathVariable String branch) {

    // Validate branch exists (throws BranchNotFoundException if not)
    branchRepository
        .findByDatasetAndName(dataset, branch)
        .orElseThrow(() -> new BranchNotFoundException(branch));

    // Analyze branch and get suggestions
    List<PrefixSuggestion> suggestions = prefixSuggestionService
        .analyzeBranch(dataset, branch);

    SuggestedPrefixesResponse response = new SuggestedPrefixesResponse(
        dataset,
        branch,
        suggestions
    );

    return ResponseEntity.ok(response);
  }
}
