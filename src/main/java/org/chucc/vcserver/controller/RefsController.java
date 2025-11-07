package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.chucc.vcserver.dto.RefsListResponse;
import org.chucc.vcserver.service.RefService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for refs (branches and tags) management endpoints.
 */
@RestController
@RequestMapping("/{dataset}/version")
@Tag(name = "Version Control", description = "Refs management operations")
public class RefsController {

  private final RefService refService;

  /**
   * Constructor for RefsController.
   *
   * @param refService the ref service
   */
  public RefsController(RefService refService) {
    this.refService = refService;
  }

  /**
   * List all refs (branches and tags).
   *
   * @param dataset the dataset name
   * @return list of all refs
   */
  @GetMapping(value = "/refs", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List all refs",
      description = "List all branches and tags with their target commits"
  )
  @ApiResponse(
      responseCode = "200",
      description = "Refs list",
      content = @Content(
          mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @Schema(implementation = RefsListResponse.class)
      )
  )
  public ResponseEntity<RefsListResponse> listRefs(
      @Parameter(description = "Dataset name", required = true)
      @PathVariable String dataset
  ) {
    var refs = refService.getAllRefs(dataset);
    return ResponseEntity.ok(new RefsListResponse(refs));
  }
}
