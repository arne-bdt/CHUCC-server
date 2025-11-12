package org.chucc.vcserver.exception;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import org.chucc.vcserver.dto.ConflictProblemDetail;
import org.chucc.vcserver.dto.ProblemDetail;
import org.chucc.vcserver.exception.kafka.InvalidKafkaConfigurationException;
import org.chucc.vcserver.exception.kafka.KafkaAuthorizationException;
import org.chucc.vcserver.exception.kafka.KafkaOperationException;
import org.chucc.vcserver.exception.kafka.KafkaQuotaExceededException;
import org.chucc.vcserver.exception.kafka.KafkaUnavailableException;
import org.chucc.vcserver.exception.kafka.TopicCreationException;
import org.chucc.vcserver.util.ErrorResponseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Global exception handler for Version Control Server exceptions.
 * Converts VcException instances to RFC 7807 problem+json responses.
 */
@ControllerAdvice
public class VcExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(VcExceptionHandler.class);

  private static final MediaType PROBLEM_JSON =
      MediaType.parseMediaType("application/problem+json");

  private final MeterRegistry meterRegistry;

  /**
   * Constructs a VcExceptionHandler.
   *
   * @param meterRegistry the meter registry for metrics
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "MeterRegistry is a Spring-managed bean, not a mutable data structure"
  )
  public VcExceptionHandler(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /**
   * Handle merge conflict exceptions.
   *
   * @param ex the merge conflict exception
   * @return RFC 7807 problem+json response with conflict details
   */
  @ExceptionHandler(MergeConflictException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ConflictProblemDetail> handleMergeConflict(
      MergeConflictException ex) {
    ConflictProblemDetail problem = new ConflictProblemDetail(
        "/problems/merge-conflict",
        ex.getMessage(),
        ex.getStatus(),
        ex.getCode(),
        ex.getConflicts()
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.valueOf(ex.getStatus()));
  }

  /**
   * Handle rebase conflict exceptions.
   *
   * @param ex the rebase conflict exception
   * @return RFC 7807 problem+json response with conflict details
   */
  @ExceptionHandler(RebaseConflictException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ConflictProblemDetail> handleRebaseConflict(
      RebaseConflictException ex) {
    ConflictProblemDetail problem = new ConflictProblemDetail(
        "/problems/rebase-conflict",
        ex.getMessage(),
        ex.getStatus(),
        ex.getCode(),
        ex.getConflicts()
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.valueOf(ex.getStatus()));
  }

  /**
   * Handle cherry-pick conflict exceptions.
   *
   * @param ex the cherry-pick conflict exception
   * @return RFC 7807 problem+json response with conflict details
   */
  @ExceptionHandler(CherryPickConflictException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ConflictProblemDetail> handleCherryPickConflict(
      CherryPickConflictException ex) {
    ConflictProblemDetail problem = new ConflictProblemDetail(
        "/problems/cherry-pick-conflict",
        ex.getMessage(),
        ex.getStatus(),
        ex.getCode(),
        ex.getConflicts()
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.valueOf(ex.getStatus()));
  }

  /**
   * Handle concurrent write conflict exceptions with detailed conflict information.
   *
   * @param ex the concurrent write conflict exception
   * @return RFC 7807 problem+json response with conflict details
   */
  @ExceptionHandler(ConcurrentWriteConflictException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<?> handleConcurrentWriteConflict(
      ConcurrentWriteConflictException ex) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    // If conflicts are present, return ConflictProblemDetail
    if (!ex.getConflicts().isEmpty()) {
      ConflictProblemDetail problem = new ConflictProblemDetail(
          "/problems/concurrent-write-conflict",
          ex.getMessage(),
          ex.getStatus(),
          ex.getCode(),
          ex.getConflicts()
      );
      return new ResponseEntity<>(problem, headers, HttpStatus.valueOf(ex.getStatus()));
    }

    // Otherwise, return simple ProblemDetail
    ProblemDetail problem = new ProblemDetail(
        ex.getMessage(),
        ex.getStatus(),
        ex.getCode()
    );
    return new ResponseEntity<>(problem, headers, HttpStatus.valueOf(ex.getStatus()));
  }

  /**
   * Handle precondition failed exceptions with detailed information.
   *
   * @param ex the precondition failed exception
   * @return RFC 7807 problem+json response with expected and actual values
   */
  @ExceptionHandler(PreconditionFailedException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handlePreconditionFailed(
      PreconditionFailedException ex) {
    ProblemDetail problem = ErrorResponseBuilder.buildPreconditionFailed(
        ex.getExpected(),
        ex.getActual()
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.valueOf(ex.getStatus()));
  }

  /**
   * Handle graph not found exceptions.
   *
   * @param ex the graph not found exception
   * @return RFC 7807 problem+json response
   */
  @ExceptionHandler(GraphNotFoundException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handleGraphNotFound(GraphNotFoundException ex) {
    // Extract graph IRI from message if available
    String message = ex.getMessage();
    String graphIri = message.substring(message.lastIndexOf(":") + 1).trim();

    ProblemDetail problem = ErrorResponseBuilder.buildGraphNotFound(graphIri);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.NOT_FOUND);
  }

  /**
   * Handle selector conflict exceptions with helpful hints.
   *
   * @param ex the selector conflict exception
   * @return RFC 7807 problem+json response
   */
  @ExceptionHandler(SelectorConflictException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handleSelectorConflict(SelectorConflictException ex) {
    ProblemDetail problem = ErrorResponseBuilder.buildSelectorConflict(ex.getMessage());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.BAD_REQUEST);
  }

  /**
   * Handle all VcException instances.
   *
   * @param ex the version control exception
   * @return RFC 7807 problem+json response
   */
  @ExceptionHandler(VcException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handleVcException(VcException ex) {
    ProblemDetail problem = new ProblemDetail(
        ex.getMessage(),
        ex.getStatus(),
        ex.getCode()
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.valueOf(ex.getStatus()));
  }

  /**
   * Handle dataset already exists exceptions.
   *
   * @param ex the dataset already exists exception
   * @return RFC 7807 problem+json response with 409 Conflict
   */
  @ExceptionHandler(DatasetAlreadyExistsException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handleDatasetAlreadyExists(
      DatasetAlreadyExistsException ex) {
    ProblemDetail problem = new ProblemDetail(
        ex.getMessage(),
        HttpStatus.CONFLICT.value(),
        "dataset_already_exists"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.CONFLICT);
  }

  /**
   * Handle branch already exists exceptions.
   *
   * @param ex the branch already exists exception
   * @return RFC 7807 problem+json response with 409 Conflict
   */
  @ExceptionHandler(BranchAlreadyExistsException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handleBranchAlreadyExists(
      BranchAlreadyExistsException ex) {
    ProblemDetail problem = new ProblemDetail(
        ex.getMessage(),
        HttpStatus.CONFLICT.value(),
        "branch_already_exists"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.CONFLICT);
  }

  /**
   * Handle IllegalArgumentException (e.g., invalid timestamp format).
   *
   * @param ex the illegal argument exception
   * @return RFC 7807 problem+json response with 400 Bad Request
   */
  @ExceptionHandler(IllegalArgumentException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
    ProblemDetail problem = new ProblemDetail(
        ex.getMessage(),
        HttpStatus.BAD_REQUEST.value(),
        "invalid_argument"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.BAD_REQUEST);
  }

  /**
   * Handle Kafka unavailable exceptions (transient errors).
   *
   * @param ex the Kafka unavailable exception
   * @return RFC 7807 problem+json response with 503 Service Unavailable
   */
  @ExceptionHandler(KafkaUnavailableException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handleKafkaUnavailable(KafkaUnavailableException ex) {
    logger.warn("Kafka cluster unavailable: {}", ex.getMessage());
    meterRegistry.counter("kafka.errors", "type", "unavailable").increment();

    ProblemDetail problem = new ProblemDetail(
        "Event store is temporarily unavailable. Please try again later.",
        HttpStatus.SERVICE_UNAVAILABLE.value(),
        "kafka_unavailable"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);
    headers.add("Retry-After", "30");  // Retry after 30 seconds

    return new ResponseEntity<>(problem, headers, HttpStatus.SERVICE_UNAVAILABLE);
  }

  /**
   * Handle Kafka authorization exceptions (fatal configuration errors).
   *
   * @param ex the Kafka authorization exception
   * @return RFC 7807 problem+json response with 500 Internal Server Error
   */
  @ExceptionHandler(KafkaAuthorizationException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handleKafkaAuthorization(KafkaAuthorizationException ex) {
    logger.error("CRITICAL: Kafka authorization failure: {}", ex.getMessage(), ex);
    meterRegistry.counter("kafka.errors", "type", "authorization").increment();

    ProblemDetail problem = new ProblemDetail(
        "Server configuration error: insufficient permissions to manage event store",
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        "kafka_authorization_failed"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /**
   * Handle Kafka quota exceeded exceptions.
   *
   * @param ex the Kafka quota exceeded exception
   * @return RFC 7807 problem+json response with 507 Insufficient Storage
   */
  @ExceptionHandler(KafkaQuotaExceededException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handleKafkaQuotaExceeded(KafkaQuotaExceededException ex) {
    logger.warn("Kafka quota exceeded: {}", ex.getMessage());
    meterRegistry.counter("kafka.errors", "type", "quota_exceeded").increment();

    ProblemDetail problem = new ProblemDetail(
        "Dataset creation failed: Event store storage quota exceeded. "
            + "Please contact your administrator.",
        HttpStatus.INSUFFICIENT_STORAGE.value(),
        "kafka_quota_exceeded"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.INSUFFICIENT_STORAGE);
  }

  /**
   * Handle invalid Kafka configuration exceptions.
   *
   * @param ex the invalid Kafka configuration exception
   * @return RFC 7807 problem+json response with 500 Internal Server Error
   */
  @ExceptionHandler(InvalidKafkaConfigurationException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handleInvalidKafkaConfiguration(
      InvalidKafkaConfigurationException ex) {
    logger.error("Invalid Kafka configuration: {}", ex.getMessage(), ex);
    meterRegistry.counter("kafka.errors", "type", "configuration").increment();

    ProblemDetail problem = new ProblemDetail(
        "Server configuration error: invalid event store configuration",
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        "kafka_configuration_invalid"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /**
   * Handle general topic creation exceptions.
   *
   * @param ex the topic creation exception
   * @return RFC 7807 problem+json response with 500 Internal Server Error
   */
  @ExceptionHandler(TopicCreationException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handleTopicCreation(TopicCreationException ex) {
    logger.error("Topic creation failed: {}", ex.getMessage(), ex);
    meterRegistry.counter("kafka.errors", "type", "topic_creation").increment();

    ProblemDetail problem = new ProblemDetail(
        "Failed to create dataset: event store operation failed",
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        "topic_creation_failed"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /**
   * Handle generic Kafka operation exceptions (catch-all).
   *
   * @param ex the Kafka operation exception
   * @return RFC 7807 problem+json response with 500 Internal Server Error
   */
  @ExceptionHandler(KafkaOperationException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handleKafkaOperation(KafkaOperationException ex) {
    logger.error("Kafka operation failed: {}", ex.getMessage(), ex);
    meterRegistry.counter("kafka.errors", "type", "operation").increment();

    ProblemDetail problem = new ProblemDetail(
        "Event store operation failed. Please try again later.",
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        "kafka_operation_failed"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
