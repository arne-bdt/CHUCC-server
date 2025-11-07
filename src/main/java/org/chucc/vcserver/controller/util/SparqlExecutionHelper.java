package org.chucc.vcserver.controller.util;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryParseException;
import org.chucc.vcserver.command.SparqlUpdateCommand;
import org.chucc.vcserver.command.SparqlUpdateCommandHandler;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.ResultFormat;
import org.chucc.vcserver.dto.ProblemDetail;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.exception.CommitNotFoundException;
import org.chucc.vcserver.exception.MalformedUpdateException;
import org.chucc.vcserver.exception.PreconditionFailedException;
import org.chucc.vcserver.exception.TagNotFoundException;
import org.chucc.vcserver.exception.UpdateExecutionException;
import org.chucc.vcserver.service.SparqlQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Helper class for shared SPARQL query and update execution logic.
 * Eliminates code duplication between SparqlController and VersionedSparqlController.
 */
public final class SparqlExecutionHelper {

  private SparqlExecutionHelper() {
    // Utility class - prevent instantiation
  }

  /**
   * Executes a SPARQL query and returns formatted results.
   *
   * @param materializedDataset the dataset to query
   * @param query the SPARQL query string
   * @param request the HTTP request for Accept header
   * @param queryService the SPARQL query service
   * @param commitId the commit ID for ETag header
   * @return query results with appropriate headers
   */
  public static ResponseEntity<String> executeQuery(
      Dataset materializedDataset,
      String query,
      HttpServletRequest request,
      SparqlQueryService queryService,
      CommitId commitId) {

    try {
      // Determine result format from Accept header
      ResultFormat format = SparqlControllerUtil.determineResultFormat(request.getHeader("Accept"));

      // Execute query
      String results = queryService.executeQuery(materializedDataset, query, format);

      // Return results with ETag
      return ResponseEntity.ok()
          .eTag("\"" + commitId.value() + "\"")
          .contentType(SparqlControllerUtil.getMediaType(format))
          .body(results);

    } catch (QueryParseException e) {
      return ResponseEntity
          .status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(SparqlControllerUtil.serializeProblemDetail(new ProblemDetail(
              "SPARQL query is malformed: " + e.getMessage(),
              HttpStatus.BAD_REQUEST.value(),
              "MALFORMED_QUERY")));
    } catch (BranchNotFoundException | CommitNotFoundException | TagNotFoundException e) {
      return ResponseEntity
          .status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(SparqlControllerUtil.serializeProblemDetail(new ProblemDetail(
              e.getMessage(),
              HttpStatus.NOT_FOUND.value(),
              "NOT_FOUND")));
    } catch (IllegalArgumentException e) {
      return ResponseEntity
          .status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(SparqlControllerUtil.serializeProblemDetail(new ProblemDetail(
              e.getMessage(),
              HttpStatus.BAD_REQUEST.value(),
              "INVALID_REQUEST")));
    }
  }

  /**
   * Validates and parses If-Match header for optimistic locking.
   *
   * @param ifMatch the If-Match header value
   * @return optional CommitId if valid, error response if invalid
   */
  public static Either<ResponseEntity<String>, Optional<CommitId>> parseIfMatchHeader(
      String ifMatch) {

    if (ifMatch == null || ifMatch.isBlank()) {
      return Either.right(Optional.empty());
    }

    String cleanEtag = ifMatch.replaceAll("^\"|\"$", "");
    try {
      return Either.right(Optional.of(CommitId.of(cleanEtag)));
    } catch (IllegalArgumentException e) {
      ProblemDetail problem = new ProblemDetail(
          "Invalid If-Match header: " + e.getMessage(),
          HttpStatus.BAD_REQUEST.value(),
          "INVALID_ETAG");
      ResponseEntity<String> errorResponse = ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(SparqlControllerUtil.serializeProblemDetail(problem));
      return Either.left(errorResponse);
    }
  }

  /**
   * Validates required headers for SPARQL update operations.
   *
   * @param author the SPARQL-VC-Author header value
   * @return optional error response if validation fails
   */
  public static Optional<ResponseEntity<String>> validateUpdateHeaders(String author) {
    if (author == null || author.isBlank()) {
      ProblemDetail problem = new ProblemDetail(
          "SPARQL-VC-Author header is required for UPDATE operations",
          HttpStatus.BAD_REQUEST.value(),
          "MISSING_HEADER");
      return Optional.of(ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(SparqlControllerUtil.serializeProblemDetail(problem)));
    }
    return Optional.empty();
  }

  /**
   * Executes a SPARQL update command and returns appropriate response.
   * Note: Location header must be set by caller as URL format varies by controller.
   *
   * @param command the update command to execute
   * @param updateCommandHandler the command handler
   * @return update response with commit ID or no-op response
   */
  @SuppressWarnings("PMD.AvoidDuplicateLiterals") // Duplicate error codes are acceptable
  public static ResponseEntity<String> executeUpdate(
      SparqlUpdateCommand command,
      SparqlUpdateCommandHandler updateCommandHandler) {

    try {
      VersionControlEvent event = updateCommandHandler.handle(command);

      // Handle no-op (per SPARQL 1.2 Protocol §7)
      if (event == null) {
        return ResponseEntity.noContent().build();
      }

      // Success: Return 202 Accepted with ETag and body
      // Note: Location header should be added by caller
      CommitCreatedEvent commitEvent = (CommitCreatedEvent) event;
      String commitId = commitEvent.commitId();

      return ResponseEntity.accepted()
          .eTag("\"" + commitId + "\"")
          .contentType(MediaType.APPLICATION_JSON)
          .body(String.format("{\"message\":\"Update accepted\",\"commitId\":\"%s\"}",
              commitId));

    } catch (MalformedUpdateException e) {
      ProblemDetail problem = new ProblemDetail(
          e.getMessage(),
          HttpStatus.BAD_REQUEST.value(),
          "MALFORMED_UPDATE");
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(SparqlControllerUtil.serializeProblemDetail(problem));
    } catch (PreconditionFailedException e) {
      ProblemDetail problem = new ProblemDetail(
          e.getMessage(),
          HttpStatus.PRECONDITION_FAILED.value(),
          "PRECONDITION_FAILED");
      return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(SparqlControllerUtil.serializeProblemDetail(problem));
    } catch (UpdateExecutionException e) {
      ProblemDetail problem = new ProblemDetail(
          e.getMessage(),
          HttpStatus.INTERNAL_SERVER_ERROR.value(),
          "UPDATE_EXECUTION_ERROR");
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(SparqlControllerUtil.serializeProblemDetail(problem));
    } catch (IllegalArgumentException e) {
      ProblemDetail problem = new ProblemDetail(
          e.getMessage(),
          HttpStatus.BAD_REQUEST.value(),
          "INVALID_REQUEST");
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(SparqlControllerUtil.serializeProblemDetail(problem));
    }
  }

  /**
   * Simple Either type for returning success or error values.
   *
   * @param <L> left type (error)
   * @param <R> right type (success)
   */
  public static final class Either<L, R> {
    private final L left;
    private final R right;
    private final boolean isLeft;

    private Either(L left, R right, boolean isLeft) {
      this.left = left;
      this.right = right;
      this.isLeft = isLeft;
    }

    /**
     * Creates a left (error) Either.
     *
     * @param <L> left type
     * @param <R> right type
     * @param value the left value
     * @return left Either
     */
    public static <L, R> Either<L, R> left(L value) {
      return new Either<>(value, null, true);
    }

    /**
     * Creates a right (success) Either.
     *
     * @param <L> left type
     * @param <R> right type
     * @param value the right value
     * @return right Either
     */
    public static <L, R> Either<L, R> right(R value) {
      return new Either<>(null, value, false);
    }

    /**
     * Checks if this is a left value.
     *
     * @return true if left
     */
    public boolean isLeft() {
      return isLeft;
    }

    /**
     * Gets the left value.
     *
     * @return left value
     */
    public L getLeft() {
      return left;
    }

    /**
     * Gets the right value.
     *
     * @return right value
     */
    public R getRight() {
      return right;
    }
  }
}
