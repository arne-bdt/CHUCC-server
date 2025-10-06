package org.chucc.vcserver.controller;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.util.SelectorValidator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SPARQL 1.2 Protocol endpoint with version control support.
 */
@RestController
@RequestMapping("/sparql")
@Tag(name = "SPARQL Protocol", description = "SPARQL 1.2 query and update operations")
public class SparqlController {

  private final VersionControlProperties vcProperties;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring-managed config bean, not modified")
  public SparqlController(VersionControlProperties vcProperties) {
    this.vcProperties = vcProperties;
  }

  /**
   * Execute a SPARQL query via HTTP GET.
   *
   * @param query SPARQL query string
   * @param defaultGraphUri default graph URIs
   * @param namedGraphUri named graph URIs
   * @param branch target branch
   * @param commit target commit
   * @param asOf timestamp for time-travel query
   * @return query results (501 stub)
   */
  @GetMapping(produces = {
      "application/sparql-results+json",
      "application/sparql-results+xml",
      "text/csv",
      "text/tab-separated-values"
  })
  @Operation(
      summary = "SPARQL Query (GET)",
      description = "Execute a SPARQL query using HTTP GET. "
          + "Version control: Query parameters branch, commit, or asOf select the dataset state. "
          + "Without these, queries execute against the default branch HEAD."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Query results",
      headers = @Header(
          name = "ETag",
          description = "Commit id of the queried state",
          schema = @Schema(type = "string")
      )
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "501",
      description = "Not Implemented",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<String> querySparqlGet(
      @Parameter(description = "The SPARQL query string", required = true)
      @RequestParam String query,
      @Parameter(description = "Default graph URI(s)")
      @RequestParam(name = "default-graph-uri", required = false) String[] defaultGraphUri,
      @Parameter(description = "Named graph URI(s)")
      @RequestParam(name = "named-graph-uri", required = false) String[] namedGraphUri,
      @Parameter(description = "Target branch for query")
      @RequestParam(required = false) String branch,
      @Parameter(description = "Target commit for query (read-only)")
      @RequestParam(required = false) String commit,
      @Parameter(description = "Query branch state at or before this timestamp (ISO8601)")
      @RequestParam(required = false) String asOf
  ) {
    // Validate selector mutual exclusion per §4
    SelectorValidator.validateMutualExclusion(branch, commit, asOf);

    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body("{\"title\":\"Not Implemented\",\"status\":501}");
  }

  /**
   * Execute a SPARQL query or update via HTTP POST.
   *
   * @param body request body (query or update)
   * @param vcBranch target branch header
   * @param commitMessage commit message header
   * @param commitAuthor commit author header
   * @param contentType content type header
   * @return query results or update confirmation (501 stub)
   */
  @PostMapping(
      consumes = {
          MediaType.APPLICATION_FORM_URLENCODED_VALUE,
          "application/sparql-query",
          "application/sparql-update"
      },
      produces = {
          "application/sparql-results+json",
          "application/sparql-results+xml",
          MediaType.APPLICATION_JSON_VALUE
      }
  )
  @Operation(
      summary = "SPARQL Query or Update (POST)",
      description = "Execute a SPARQL query or update using HTTP POST. "
          + "Content-Type application/sparql-query → Query operation. "
          + "Content-Type application/sparql-update → Update operation (creates commit). "
          + "Content-Type application/x-www-form-urlencoded → Query or update based on form fields."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Query results or update confirmation with representation",
      headers = {
          @Header(
              name = "ETag",
              description = "Commit id (new for updates, queried for queries)",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "Location",
              description = "URI of created commit (for updates)",
              schema = @Schema(type = "string")
          )
      }
  )
  @ApiResponse(
      responseCode = "204",
      description = "Update succeeded without representation",
      headers = {
          @Header(
              name = "ETag",
              description = "New commit id",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "Location",
              description = "URI of created commit",
              schema = @Schema(type = "string")
          )
      }
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "412",
      description = "Precondition Failed - ETag mismatch",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "501",
      description = "Not Implemented",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<String> executeSparqlPost(
      @RequestBody String body,
      @Parameter(description = "Target branch for update operations")
      @RequestHeader(name = "SPARQL-VC-Branch", required = false) String vcBranch,
      @Parameter(description = "Commit message (required for updates)")
      @RequestHeader(name = "SPARQL-VC-Commit-Message", required = false) String commitMessage,
      @Parameter(description = "Commit author (required for updates)")
      @RequestHeader(name = "SPARQL-VC-Commit-Author", required = false) String commitAuthor,
      @RequestHeader(name = "Content-Type", required = false) String contentType
  ) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body("{\"title\":\"Not Implemented\",\"status\":501}");
  }

  /**
   * Discover SPARQL endpoint capabilities via HTTP OPTIONS.
   * Advertises SPARQL-Version-Control support and enabled features.
   *
   * @return OPTIONS response with capability headers
   */
  @RequestMapping(method = RequestMethod.OPTIONS)
  @Operation(
      summary = "Discover endpoint capabilities",
      description = "Returns HTTP OPTIONS response advertising SPARQL 1.2 Protocol support, "
          + "SPARQL-Version-Control extension, conformance level, and enabled features."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Capability information",
      headers = {
          @Header(
              name = "Allow",
              description = "Allowed HTTP methods",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "Accept-Patch",
              description = "Supported patch formats (text/rdf-patch)",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "SPARQL-Version-Control",
              description = "Version control extension version",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "SPARQL-VC-Level",
              description = "Conformance level (1=Basic, 2=Advanced)",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "SPARQL-VC-Features",
              description = "Enabled features (comma-separated)",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "Link",
              description = "Link to version control API",
              schema = @Schema(type = "string")
          )
      }
  )
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<Void> options() {
    HttpHeaders headers = new HttpHeaders();

    // Standard HTTP OPTIONS
    headers.add(HttpHeaders.ALLOW, "GET, POST, OPTIONS");

    // SPARQL Version Control extension support
    headers.add("SPARQL-Version-Control", "1.0");
    headers.add("SPARQL-VC-Level", String.valueOf(vcProperties.getLevel()));

    // Advertise RDF Patch support
    if (vcProperties.isRdfPatchEnabled()) {
      headers.add(HttpHeaders.ACCEPT_PATCH, "text/rdf-patch");
    }

    // Build enabled features list
    List<String> features = new ArrayList<>();
    if (vcProperties.isCommitsEnabled()) {
      features.add("commits");
    }
    if (vcProperties.isBranchesEnabled()) {
      features.add("branches");
    }
    if (vcProperties.isHistoryEnabled()) {
      features.add("history");
    }
    if (vcProperties.isRdfPatchEnabled()) {
      features.add("rdf-patch");
    }
    if (vcProperties.isMergeEnabled()) {
      features.add("merge");
    }
    if (vcProperties.isConflictDetectionEnabled()) {
      features.add("conflict-detection");
    }
    if (vcProperties.isTagsEnabled()) {
      features.add("tags");
    }
    if (vcProperties.isRevertEnabled()) {
      features.add("revert");
    }
    if (vcProperties.isResetEnabled()) {
      features.add("reset");
    }
    if (vcProperties.isCherryPickEnabled()) {
      features.add("cherry-pick");
    }
    if (vcProperties.isBlameEnabled()) {
      features.add("blame");
    }

    if (!features.isEmpty()) {
      headers.add("SPARQL-VC-Features", String.join(", ", features));
    }

    // Link to version control API
    headers.add(HttpHeaders.LINK, "</version>; rel=\"version-control\"");

    return ResponseEntity.ok().headers(headers).build();
  }
}
