package org.chucc.vcserver.monitoring;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Monitors materialized views and logs statistics periodically.
 *
 * <p>This component runs a scheduled task to log information about
 * materialized graphs. Useful for operational monitoring and debugging.
 *
 * <p>Logging frequency: Every 5 minutes (300,000 ms)
 */
@Component
public class MaterializedViewsMonitor {

  private static final Logger logger = LoggerFactory.getLogger(MaterializedViewsMonitor.class);

  private final MaterializedBranchRepository materializedBranchRepo;

  /**
   * Constructs the monitor.
   *
   * @param materializedBranchRepo the repository to monitor
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repository is a Spring-managed bean and is intentionally shared")
  public MaterializedViewsMonitor(MaterializedBranchRepository materializedBranchRepo) {
    this.materializedBranchRepo = materializedBranchRepo;
  }

  /**
   * Log materialized view statistics every 5 minutes.
   *
   * <p>This scheduled task logs the current number of materialized graphs.
   * Additional metrics (like memory warnings) can be added here.
   */
  @Scheduled(fixedRate = 300000)  // 5 minutes
  public void logStatistics() {
    int graphCount = materializedBranchRepo.getGraphCount();

    logger.info("Materialized Views Stats: {} graphs", graphCount);

    // Future enhancement: Add memory warning if exceeds threshold
    // Example:
    // if (memoryUsageBytes > threshold) {
    //   logger.warn("Materialized views memory usage exceeds threshold: {} MB", mb);
    // }
  }
}
