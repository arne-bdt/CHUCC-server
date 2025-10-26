package org.chucc.vcserver.controller;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import org.chucc.vcserver.command.CreateDatasetCommand;
import org.chucc.vcserver.command.CreateDatasetCommandHandler;
import org.chucc.vcserver.command.DeleteDatasetCommand;
import org.chucc.vcserver.command.DeleteDatasetCommandHandler;
import org.chucc.vcserver.config.KafkaProperties;
import org.chucc.vcserver.dto.CreateDatasetRequest;
import org.chucc.vcserver.dto.DatasetCreationResponse;
import org.chucc.vcserver.event.DatasetCreatedEvent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Dataset management endpoints for version control.
 */
@RestController
@RequestMapping("/version/datasets")
@Tag(name = "Version Control", description = "Dataset management operations")
public class DatasetController {

  private final DeleteDatasetCommandHandler deleteDatasetCommandHandler;
  private final CreateDatasetCommandHandler createDatasetCommandHandler;
  private final KafkaProperties kafkaProperties;

  /**
   * Constructs a DatasetController.
   *
   * @param deleteDatasetCommandHandler the delete dataset command handler
   * @param createDatasetCommandHandler the create dataset command handler
   * @param kafkaProperties the Kafka properties
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Command handlers are Spring-managed beans and are intentionally shared")
  public DatasetController(
      DeleteDatasetCommandHandler deleteDatasetCommandHandler,
      CreateDatasetCommandHandler createDatasetCommandHandler,
      KafkaProperties kafkaProperties) {
    this.deleteDatasetCommandHandler = deleteDatasetCommandHandler;
    this.createDatasetCommandHandler = createDatasetCommandHandler;
    this.kafkaProperties = kafkaProperties;
  }

  /**
   * Create a new dataset.
   *
   * @param name the dataset name
   * @param request the dataset creation request (optional)
   * @param author the author of the dataset creation
   * @return the created dataset information
   */
  @PostMapping(
      value = "/{name}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  @Operation(
      summary = "Create dataset",
      description = "Create a new dataset with automatic Kafka topic creation"
  )
  @ApiResponse(
      responseCode = "202",
      description = "Dataset creation accepted (eventual consistency)",
      headers = {
          @Header(
              name = "Location",
              description = "URL of the created dataset",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "SPARQL-VC-Status",
              description = "Status of the operation (pending)",
              schema = @Schema(type = "string")
          )
      },
      content = @Content(
          mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @Schema(implementation = DatasetCreationResponse.class)
      )
  )
  @ApiResponse(
      responseCode = "400",
      description = "Invalid dataset name or request"
  )
  @ApiResponse(
      responseCode = "409",
      description = "Dataset already exists"
  )
  public ResponseEntity<DatasetCreationResponse> createDataset(
      @Parameter(description = "Dataset name (must be Kafka-compatible)")
      @PathVariable String name,
      @RequestBody(required = false) CreateDatasetRequest request,
      @Parameter(description = "Author of the dataset creation")
      @RequestHeader(value = "SPARQL-VC-Author", required = false) String author
  ) {
    // Validate request
    if (request != null) {
      request.validate();
    }

    // Use author from header, or default to "anonymous"
    String actualAuthor = (author != null && !author.isBlank()) ? author : "anonymous";

    // Create command
    CreateDatasetCommand command = new CreateDatasetCommand(
        name,
        request != null ? Optional.ofNullable(request.description()) : Optional.empty(),
        actualAuthor,
        request != null ? Optional.ofNullable(request.initialGraph()) : Optional.empty(),
        request != null ? request.kafka() : null
    );

    // Execute command (synchronous handling with async event publishing)
    DatasetCreatedEvent event = (DatasetCreatedEvent) createDatasetCommandHandler.handle(command);

    // Build response
    DatasetCreationResponse response = new DatasetCreationResponse(
        event.dataset(),
        event.description(),
        event.mainBranch(),
        event.initialCommitId(),
        kafkaProperties.getTopicName(event.dataset()),
        event.timestamp()
    );

    // Build Location header
    String location = ServletUriComponentsBuilder
        .fromCurrentRequest()
        .build()
        .toUriString();

    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.LOCATION, location);
    headers.add("SPARQL-VC-Status", "pending");

    // Return 202 Accepted (CQRS pattern - eventual consistency)
    return ResponseEntity
        .status(HttpStatus.ACCEPTED)
        .headers(headers)
        .body(response);
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
