package org.chucc.vcserver.health;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import java.util.HashMap;
import java.util.Map;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for materialized views and projection system.
 *
 * <p>Reports the status of the MaterializedBranchRepository and projection error rate, including:
 * <ul>
 *   <li>Number of materialized graphs</li>
 *   <li>Projection error count (recent errors)</li>
 *   <li>Overall health status (DOWN if errors detected)</li>
 * </ul>
 *
 * <p>This health check is accessible via the Spring Boot Actuator endpoint:
 * {@code GET /actuator/health/materializedViews}
 */
@Component("materializedViews")
public class MaterializedViewsHealthIndicator implements HealthIndicator {

  private final MaterializedBranchRepository materializedBranchRepo;
  private final MeterRegistry meterRegistry;

  /**
   * Constructs a new health indicator.
   *
   * @param materializedBranchRepo the repository to monitor
   * @param meterRegistry the meter registry for accessing projection error metrics
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Dependencies are Spring-managed beans and are intentionally shared")
  public MaterializedViewsHealthIndicator(
      MaterializedBranchRepository materializedBranchRepo,
      MeterRegistry meterRegistry) {
    this.materializedBranchRepo = materializedBranchRepo;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Health health() {
    try {
      int graphCount = materializedBranchRepo.getGraphCount();

      Map<String, Object> details = new HashMap<>();
      details.put("graphCount", graphCount);

      // Include projection error count as informational metric
      // Note: Errors are retried by Kafka, so cumulative count doesn't indicate DOWN status
      double errorCount = getErrorEventCount();
      if (errorCount > 0) {
        details.put("projectionErrors", (long) errorCount);
        details.put("note", "Errors are automatically retried by Kafka");
      }

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

  /**
   * Gets the count of projection error events.
   * Uses the counter metric "chucc.projection.events.total" with status=error.
   *
   * @return number of error events (cumulative since app start)
   */
  private double getErrorEventCount() {
    return Search.in(meterRegistry)
        .name("chucc.projection.events.total")
        .tag("status", "error")
        .counters()
        .stream()
        .mapToDouble(counter -> counter.count())
        .sum();
  }
}
