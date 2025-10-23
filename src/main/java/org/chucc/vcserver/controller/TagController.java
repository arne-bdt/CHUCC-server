package org.chucc.vcserver.controller;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.chucc.vcserver.dto.ProblemDetail;
import org.chucc.vcserver.service.TagService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tag management endpoints for version control.
 */
@RestController
@RequestMapping("/{dataset}/version/tags")
@Tag(name = "Version Control", description = "Tag management operations")
public class TagController {

  private final TagService tagService;

  /**
   * Constructor for TagController.
   *
   * @param tagService the tag service
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring-managed bean is intentionally a shared reference"
  )
  public TagController(TagService tagService) {
    this.tagService = tagService;
  }

  /**
   * List all tags.
   *
   * @return list of tags (501 stub)
   */
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "List tags", description = "List all tags in the repository")
  @ApiResponse(
      responseCode = "200",
      description = "Tags",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "501",
      description = "Not Implemented",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<String> listTags() {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body("{\"title\":\"Not Implemented\",\"status\":501}");
  }

  /**
   * Create a new immutable tag.
   *
   * @param tagRequest tag creation request
   * @return created tag (501 stub)
   */
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  @Operation(
      summary = "Create tag (immutable)",
      description = "Create a new immutable tag pointing to a commit"
  )
  @ApiResponse(
      responseCode = "201",
      description = "Tag created",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "409",
      description = "Tag already exists",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "501",
      description = "Not Implemented",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<String> createTag(@RequestBody String tagRequest) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body("{\"title\":\"Not Implemented\",\"status\":501}");
  }

  /**
   * Get tag details.
   *
   * @param dataset the dataset name
   * @param name the tag name
   * @return tag details if found, 404 otherwise
   */
  @GetMapping(value = "/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get tag details",
      description = "Retrieve tag details and target commit"
  )
  @ApiResponse(responseCode = "200", description = "Tag details")
  @ApiResponse(
      responseCode = "404",
      description = "Tag not found",
      content = @Content(mediaType = "application/problem+json")
  )
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<?> getTag(
      @PathVariable String dataset,
      @PathVariable String name
  ) {
    return tagService.getTagDetails(dataset, name)
        .<ResponseEntity<?>>map(ResponseEntity::ok)
        .orElseGet(() -> {
          ProblemDetail problem = new ProblemDetail(
              "Tag not found: " + name,
              HttpStatus.NOT_FOUND.value(),
              "tag_not_found"
          );
          HttpHeaders headers = new HttpHeaders();
          headers.setContentType(MediaType.parseMediaType("application/problem+json"));
          return new ResponseEntity<>(problem, headers, HttpStatus.NOT_FOUND);
        });
  }

  /**
   * Delete a tag.
   *
   * @param dataset the dataset name
   * @param name the tag name
   * @return 204 if deleted, 404 if not found, 403 if forbidden
   */
  @DeleteMapping("/{name}")
  @Operation(
      summary = "Delete tag",
      description = "Delete a tag (if server policy allows)"
  )
  @ApiResponse(responseCode = "204", description = "Tag deleted")
  @ApiResponse(
      responseCode = "404",
      description = "Tag not found",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "403",
      description = "Forbidden - server policy prohibits tag deletion",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<Void> deleteTag(
      @PathVariable String dataset,
      @PathVariable String name
  ) {
    tagService.deleteTag(dataset, name);

    // Build response headers
    HttpHeaders headers = new HttpHeaders();
    headers.set("SPARQL-VC-Status", "pending");

    return ResponseEntity.accepted().headers(headers).build();
  }
}
