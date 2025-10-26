package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.chucc.vcserver.health.AllTopicsHealthReport;
import org.chucc.vcserver.health.HealingResult;
import org.chucc.vcserver.health.KafkaTopicHealthChecker;
import org.chucc.vcserver.health.TopicHealer;
import org.chucc.vcserver.health.TopicHealthReport;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Kafka topic health checks and healing.
 * Provides endpoints to check topic health and manually heal missing topics.
 */
@RestController
@RequestMapping("/actuator/kafka")
@Tag(name = "Kafka Health", description = "Kafka topic health monitoring and healing")
public class KafkaHealthController {

  private final KafkaTopicHealthChecker healthChecker;
  private final TopicHealer topicHealer;

  /**
   * Constructs a KafkaHealthController.
   *
   * @param healthChecker the health checker
   * @param topicHealer the topic healer
   */
  public KafkaHealthController(
      KafkaTopicHealthChecker healthChecker,
      TopicHealer topicHealer) {
    this.healthChecker = healthChecker;
    this.topicHealer = topicHealer;
  }

  /**
   * Gets detailed health report for all managed Kafka topics.
   *
   * @return health report for all topics
   */
  @GetMapping(value = "/health-detailed", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get detailed Kafka health",
      description = "Returns health status of all managed Kafka topics"
  )
  @ApiResponse(
      responseCode = "200",
      description = "Health report retrieved successfully",
      content = @Content(
          mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @Schema(implementation = AllTopicsHealthReport.class)
      )
  )
  @ApiResponse(
      responseCode = "503",
      description = "One or more topics are unhealthy",
      content = @Content(
          mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @Schema(implementation = AllTopicsHealthReport.class)
      )
  )
  public ResponseEntity<AllTopicsHealthReport> getDetailedHealth() {
    AllTopicsHealthReport report = healthChecker.checkAll();

    // Return 503 if any topics are unhealthy
    if (!report.overallHealthy()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(report);
    }

    return ResponseEntity.ok(report);
  }

  /**
   * Gets health report for a specific dataset's topic.
   *
   * @param dataset the dataset name
   * @return health report for the topic
   */
  @GetMapping(value = "/topics/{dataset}/health", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get topic health",
      description = "Returns health status of a specific dataset's Kafka topic"
  )
  @ApiResponse(
      responseCode = "200",
      description = "Health report retrieved successfully",
      content = @Content(
          mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @Schema(implementation = TopicHealthReport.class)
      )
  )
  public ResponseEntity<TopicHealthReport> getTopicHealth(
      @Parameter(description = "Dataset name", required = true)
      @PathVariable String dataset) {
    TopicHealthReport report = healthChecker.checkTopic(dataset);
    return ResponseEntity.ok(report);
  }

  /**
   * Manually heals (recreates) a missing topic for a dataset.
   * Requires explicit confirmation to prevent accidental execution.
   *
   * @param dataset the dataset name
   * @param confirm confirmation flag (must be true)
   * @return healing result
   */
  @PostMapping(value = "/topics/{dataset}/heal", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Heal dataset topic",
      description = "Manually recreates a missing Kafka topic for a dataset. "
          + "Requires confirm=true to prevent accidental execution."
  )
  @ApiResponse(
      responseCode = "200",
      description = "Healing completed successfully",
      content = @Content(
          mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = @Schema(implementation = HealingResult.class)
      )
  )
  @ApiResponse(
      responseCode = "400",
      description = "Confirmation required or dataset not found"
  )
  @ApiResponse(
      responseCode = "500",
      description = "Healing failed"
  )
  public ResponseEntity<HealingResult> healTopic(
      @Parameter(description = "Dataset name", required = true)
      @PathVariable String dataset,
      @Parameter(description = "Confirmation flag (must be true)", required = true)
      @RequestParam(defaultValue = "false") boolean confirm) {

    // Require explicit confirmation
    if (!confirm) {
      HealingResult result = HealingResult.failure(
          dataset,
          "Confirmation required (add ?confirm=true)"
      );
      return ResponseEntity.badRequest().body(result);
    }

    // Attempt healing
    HealingResult result = topicHealer.heal(dataset);

    if (result.success()) {
      return ResponseEntity.ok(result);
    } else {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
  }
}
