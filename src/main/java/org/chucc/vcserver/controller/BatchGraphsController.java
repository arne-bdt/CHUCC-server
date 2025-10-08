package org.chucc.vcserver.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import org.chucc.vcserver.command.BatchGraphsCommand;
import org.chucc.vcserver.command.BatchGraphsCommand.GraphOperation;
import org.chucc.vcserver.command.BatchGraphsCommandHandler;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.dto.BatchGraphsRequest;
import org.chucc.vcserver.dto.BatchGraphsResponse;
import org.chucc.vcserver.dto.ProblemDetail;
import org.chucc.vcserver.event.BatchGraphsCompletedEvent;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.service.SelectorResolutionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for batch graph operations.
 * Implements /version/batch-graphs endpoint for atomic batch updates.
 */
@RestController
@RequestMapping("/version")
@Tag(name = "Batch Operations",
    description = "Batch graph operations for atomic multi-graph updates")
public class BatchGraphsController {

  private static final String DATASET_NAME = "default";

  private final SelectorResolutionService selectorResolutionService;
  private final BatchGraphsCommandHandler commandHandler;
  private final ObjectMapper objectMapper;

  /**
   * Constructor for BatchGraphsController.
   *
   * @param selectorResolutionService service for resolving version selectors
   * @param commandHandler handler for batch graph commands
   * @param objectMapper JSON object mapper
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring-managed beans are intentionally shared references")
  public BatchGraphsController(
      SelectorResolutionService selectorResolutionService,
      BatchGraphsCommandHandler commandHandler,
      ObjectMapper objectMapper) {
    this.selectorResolutionService = selectorResolutionService;
    this.commandHandler = commandHandler;
    this.objectMapper = objectMapper;
  }

  /**
   * Executes batch graph operations (PUT, POST, PATCH, DELETE) atomically.
   *
   * @param request the batch graphs request
   * @return response with commit IDs (200), no-op (204), or error (400, 409)
   * @throws JsonProcessingException if JSON serialization fails
   */
  @PostMapping(value = "/batch-graphs",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Execute batch graph operations",
      description = "Executes multiple graph operations (PUT, POST, PATCH, DELETE) "
          + "atomically in a single commit or sequentially as multiple commits",
      responses = {
          @ApiResponse(
              responseCode = "200",
              description = "Batch completed successfully",
              content = @Content(schema = @Schema(implementation = BatchGraphsResponse.class))
          ),
          @ApiResponse(
              responseCode = "204",
              description = "No-op: all operations resulted in no changes"
          ),
          @ApiResponse(
              responseCode = "400",
              description = "Bad request: invalid batch request",
              content = @Content(schema = @Schema(implementation = ProblemDetail.class))
          ),
          @ApiResponse(
              responseCode = "409",
              description = "Conflict: concurrent writes detected",
              content = @Content(schema = @Schema(implementation = ProblemDetail.class))
          )
      }
  )
  public ResponseEntity<String> executeBatch(@RequestBody BatchGraphsRequest request)
      throws JsonProcessingException {
    // Validate request
    request.validate();

    // Resolve branch selector to commit ID
    CommitId baseCommit = selectorResolutionService.resolve(
        DATASET_NAME,
        request.getBranch(),
        null,  // No commit selector
        null   // No asOf selector
    );

    // Convert DTO operations to command operations
    List<GraphOperation> operations = new ArrayList<>();
    for (BatchGraphsRequest.GraphOperation dtoOp : request.getOperations()) {
      boolean isDefaultGraph = dtoOp.getGraph() == null || dtoOp.getGraph().isEmpty();
      operations.add(new GraphOperation(
          dtoOp.getMethod(),
          dtoOp.getGraph(),
          isDefaultGraph,
          dtoOp.getData(),
          dtoOp.getContentType(),
          dtoOp.getPatch()
      ));
    }

    // Create and execute command
    BatchGraphsCommand command = new BatchGraphsCommand(
        DATASET_NAME,
        request.getBranch(),
        baseCommit,
        operations,
        request.getAuthor(),
        request.getMessage(),
        request.getMode()
    );

    VersionControlEvent event = commandHandler.handle(command);

    // Handle no-op case
    if (event == null) {
      return ResponseEntity.noContent().build();
    }

    // Note: Event publishing is handled by a separate mechanism (not in controller)
    // Controllers just return HTTP responses based on events

    // Build response
    BatchGraphsCompletedEvent batchEvent = (BatchGraphsCompletedEvent) event;
    List<BatchGraphsResponse.BatchCommit> commits = new ArrayList<>();

    for (CommitCreatedEvent commitEvent : batchEvent.commits()) {
      // Extract operation descriptions from commit message
      List<String> operationDescs = extractOperationDescriptions(
          commitEvent,
          request.getOperations(),
          request.getMode()
      );

      commits.add(new BatchGraphsResponse.BatchCommit(
          commitEvent.commitId(),
          operationDescs
      ));
    }

    BatchGraphsResponse response = new BatchGraphsResponse(commits);
    String responseJson = objectMapper.writeValueAsString(response);

    return ResponseEntity.ok(responseJson);
  }

  private List<String> extractOperationDescriptions(
      CommitCreatedEvent commitEvent,
      List<BatchGraphsRequest.GraphOperation> operations,
      String mode) {
    if ("single".equals(mode)) {
      // All operations are in one commit
      List<String> descs = new ArrayList<>();
      for (BatchGraphsRequest.GraphOperation op : operations) {
        descs.add(formatOperation(op));
      }
      return descs;
    } else {
      // Each commit corresponds to one operation
      // Extract operation number from commit message
      int opIndex = extractOperationIndex(commitEvent.message());
      if (opIndex >= 0 && opIndex < operations.size()) {
        return List.of(formatOperation(operations.get(opIndex)));
      }
      return List.of();
    }
  }

  private int extractOperationIndex(String message) {
    // Extract "[Operation N: ...]" from message
    int start = message.indexOf("[Operation ");
    if (start < 0) {
      return -1;
    }
    int colon = message.indexOf(":", start);
    if (colon < 0) {
      return -1;
    }
    String numStr = message.substring(start + 11, colon).trim();
    try {
      return Integer.parseInt(numStr) - 1; // Convert to 0-based index
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private String formatOperation(BatchGraphsRequest.GraphOperation op) {
    String graph = (op.getGraph() == null || op.getGraph().isEmpty())
        ? "default graph"
        : op.getGraph();
    return op.getMethod() + " " + graph;
  }
}
