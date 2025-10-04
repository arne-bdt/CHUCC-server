package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Commit introspection endpoints for version control.
 */
@RestController
@RequestMapping("/version/commits")
@Tag(name = "Version Control", description = "Commit introspection operations")
public class CommitController {

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

  /**
   * Get materialized changeset for a commit.
   *
   * @param id commit id
   * @return changeset in RDF Patch format (501 stub)
   */
  @GetMapping(value = "/{id}/changes", produces = "text/rdf-patch")
  @Operation(
      summary = "Get materialized changeset",
      description = "Retrieve the changeset for a commit in RDF Patch format"
  )
  @ApiResponse(
      responseCode = "200",
      description = "Changeset",
      content = @Content(mediaType = "text/rdf-patch")
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
  public ResponseEntity<String> getCommitChanges(
      @Parameter(description = "Commit id (UUIDv7)", required = true)
      @PathVariable String id
  ) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body("{\"title\":\"Not Implemented\",\"status\":501}");
  }
}
