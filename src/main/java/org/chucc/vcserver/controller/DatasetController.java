package org.chucc.vcserver.controller;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.chucc.vcserver.command.DeleteDatasetCommand;
import org.chucc.vcserver.command.DeleteDatasetCommandHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dataset management endpoints for version control.
 */
@RestController
@RequestMapping("/version/datasets")
@Tag(name = "Version Control", description = "Dataset management operations")
public class DatasetController {

  private final DeleteDatasetCommandHandler deleteDatasetCommandHandler;

  /**
   * Constructs a DatasetController.
   *
   * @param deleteDatasetCommandHandler the delete dataset command handler
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Command handlers are Spring-managed beans and are intentionally shared")
  public DatasetController(DeleteDatasetCommandHandler deleteDatasetCommandHandler) {
    this.deleteDatasetCommandHandler = deleteDatasetCommandHandler;
  }

  /**
   * Delete a dataset.
   *
   * @param name dataset name
   * @param deleteKafkaTopic whether to delete Kafka topic (destructive!)
   * @param confirmed confirmation flag (required)
   * @param author author of the deletion
   * @return no content (204)
   */
  @DeleteMapping("/{name}")
  @Operation(
      summary = "Delete dataset",
      description = "Deletes an entire dataset including all branches, commits, and caches. "
          + "Optionally deletes the Kafka topic (irreversible!). "
          + "Requires explicit confirmation to prevent accidental deletion."
  )
  @ApiResponse(responseCode = "204", description = "Dataset deleted successfully")
  @ApiResponse(
      responseCode = "400",
      description = "Deletion not confirmed",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "403",
      description = "Cannot delete protected dataset (default)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Dataset not found",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<Void> deleteDataset(
      @Parameter(description = "Dataset name", required = true)
      @PathVariable String name,
      @Parameter(description = "Delete Kafka topic (destructive!)")
      @RequestParam(defaultValue = "false") boolean deleteKafkaTopic,
      @Parameter(description = "Confirmation flag (must be true)", required = true)
      @RequestParam(defaultValue = "false") boolean confirmed,
      @Parameter(description = "Author of the deletion operation")
      @RequestHeader(value = "X-Author", defaultValue = "anonymous") String author) {

    // Create command
    DeleteDatasetCommand command = new DeleteDatasetCommand(
        name,
        author,
        deleteKafkaTopic,
        confirmed
    );

    // Handle command (publishes event asynchronously)
    deleteDatasetCommandHandler.handle(command);

    // Return 204 No Content (response sent before repository update)
    return ResponseEntity.noContent().build();
  }
}
