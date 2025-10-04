package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tag management endpoints for version control.
 */
@RestController
@RequestMapping("/version/tags")
@Tag(name = "Version Control", description = "Tag management operations")
public class TagController {

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
}
