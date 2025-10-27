package org.chucc.vcserver.controller;

import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.exception.DatasetNotFoundException;
import org.chucc.vcserver.service.MaterializedViewRebuildService;
import org.chucc.vcserver.service.MaterializedViewRebuildService.RebuildResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for materialized view management endpoints.
 *
 * <p>These are administrative endpoints for monitoring and maintaining
 * materialized views. All endpoints are under {@code /actuator/materialized-views}
 * to align with Spring Boot Actuator conventions.
 *
 * <p><b>Security Note:</b> These endpoints should be secured in production
 * environments as they perform administrative operations.
 */
@RestController
@RequestMapping("/actuator/materialized-views")
public class MaterializedViewsController {

  private static final Logger logger =
      LoggerFactory.getLogger(MaterializedViewsController.class);

  private final MaterializedViewRebuildService rebuildService;

  /**
   * Constructs the controller.
   *
   * @param rebuildService the rebuild service
   */
  public MaterializedViewsController(MaterializedViewRebuildService rebuildService) {
    this.rebuildService = rebuildService;
  }

  /**
   * Rebuild materialized graph for a branch from commit history.
   *
   * <p>This is a manual recovery operation. It rebuilds the materialized
   * graph by replaying all commits from the branch's history. Use this when:
   * <ul>
   *   <li>Materialized graph is corrupted</li>
   *   <li>Graph is out of sync with commit history</li>
   *   <li>After system recovery or migration</li>
   * </ul>
   *
   * <p><b>Warning:</b> This operation may take time for branches with
   * long commit histories. The rebuild is atomic - the old graph is only
   * replaced if the rebuild succeeds.
   *
   * <p><b>Example:</b>
   * <pre>
   * POST /actuator/materialized-views/rebuild?dataset=mydata&amp;branch=main&amp;confirm=true
   * </pre>
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param confirm must be "true" to proceed (safety check)
   * @return rebuild statistics
   */
  @PostMapping("/rebuild")
  public ResponseEntity<RebuildResponse> rebuildBranch(
      @RequestParam("dataset") String dataset,
      @RequestParam("branch") String branch,
      @RequestParam(value = "confirm", defaultValue = "false") boolean confirm) {

    if (!confirm) {
      return ResponseEntity.badRequest()
          .body(new RebuildResponse(
              "error",
              "Rebuild requires confirm=true parameter",
              0,
              0
          ));
    }

    try {
      RebuildResult result = rebuildService.rebuildBranch(dataset, branch);

      String message = String.format("Rebuilt %d commits in %d ms",
          result.commitsProcessed(), result.durationMs());

      return ResponseEntity.ok(new RebuildResponse(
          "success",
          message,
          result.commitsProcessed(),
          result.durationMs()
      ));
    } catch (DatasetNotFoundException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(new RebuildResponse(
              "error",
              "Dataset not found: " + dataset,
              0,
              0
          ));
    } catch (BranchNotFoundException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(new RebuildResponse(
              "error",
              "Branch not found: " + branch + " in dataset: " + dataset,
              0,
              0
          ));
    } catch (Exception e) {
      logger.error("Rebuild failed for {}/{}", dataset, branch, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new RebuildResponse(
              "error",
              "Rebuild failed: " + e.getMessage(),
              0,
              0
          ));
    }
  }

  /**
   * Response DTO for rebuild endpoint.
   *
   * @param status "success" or "error"
   * @param message human-readable message
   * @param commitsProcessed number of commits processed
   * @param durationMs rebuild duration in milliseconds
   */
  public record RebuildResponse(
      String status,
      String message,
      int commitsProcessed,
      long durationMs
  ) {}
}
