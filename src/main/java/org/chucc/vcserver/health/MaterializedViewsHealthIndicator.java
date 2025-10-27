package org.chucc.vcserver.health;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashMap;
import java.util.Map;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for materialized views.
 *
 * <p>Reports the status of the MaterializedBranchRepository, including:
 * <ul>
 *   <li>Number of materialized graphs</li>
 *   <li>Overall health status</li>
 * </ul>
 *
 * <p>This health check is accessible via the Spring Boot Actuator endpoint:
 * {@code GET /actuator/health/materializedViews}
 */
@Component("materializedViews")
public class MaterializedViewsHealthIndicator implements HealthIndicator {

  private final MaterializedBranchRepository materializedBranchRepo;

  /**
   * Constructs a new health indicator.
   *
   * @param materializedBranchRepo the repository to monitor
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repository is a Spring-managed bean and is intentionally shared")
  public MaterializedViewsHealthIndicator(
      MaterializedBranchRepository materializedBranchRepo) {
    this.materializedBranchRepo = materializedBranchRepo;
  }

  @Override
  public Health health() {
    try {
      int graphCount = materializedBranchRepo.getGraphCount();

      Map<String, Object> details = new HashMap<>();
      details.put("graphCount", graphCount);
      details.put("status", "Materialized views operational");

      return Health.up()
          .withDetails(details)
          .build();
    } catch (Exception e) {
      return Health.down()
          .withException(e)
          .build();
    }
  }
}
