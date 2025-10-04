package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Batch update operations endpoint for version control.
 */
@RestController
@RequestMapping("/version/batch")
@Tag(name = "Version Control", description = "Batch update operations")
public class BatchController {

  /**
   * Apply a batch of SPARQL updates.
   *
   * @param batchRequest batch request
   * @return batch result (501 stub)
   */
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  @Operation(
      summary = "Apply batch of SPARQL Updates",
      description = "Applies a sequence atomically; either one combined commit or multiple commits"
  )
  @ApiResponse(
      responseCode = "200",
      description = "Batch result",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
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
  public ResponseEntity<String> batchUpdate(@RequestBody String batchRequest) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body("{\"title\":\"Not Implemented\",\"status\":501}");
  }
}
