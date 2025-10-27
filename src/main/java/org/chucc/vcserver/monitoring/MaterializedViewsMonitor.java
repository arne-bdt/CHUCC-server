package org.chucc.vcserver.monitoring;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.chucc.vcserver.config.MaterializedViewsProperties;
import org.chucc.vcserver.repository.InMemoryMaterializedBranchRepository;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Monitors materialized views and logs statistics periodically.
 *
 * <p>This component runs a scheduled task to log information about
 * materialized graphs and cache statistics. Useful for operational
 * monitoring and debugging.
 *
 * <p>Logging frequency: Every 5 minutes (300,000 ms)
 */
@Component
public class MaterializedViewsMonitor {

  private static final Logger logger = LoggerFactory.getLogger(MaterializedViewsMonitor.class);

  private final InMemoryMaterializedBranchRepository materializedBranchRepo;
  private final MaterializedViewsProperties properties;

  /**
   * Constructs the monitor.
   *
   * @param materializedBranchRepo the repository to monitor
   * @param properties the materialized views configuration properties
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Dependencies are Spring-managed beans and are intentionally shared")
  public MaterializedViewsMonitor(
      MaterializedBranchRepository materializedBranchRepo,
      MaterializedViewsProperties properties) {
    // Cast to InMemoryMaterializedBranchRepository to access cache stats
    this.materializedBranchRepo = (InMemoryMaterializedBranchRepository) materializedBranchRepo;
    this.properties = properties;
  }

  /**
   * Log materialized view statistics every 5 minutes.
   *
   * <p>This scheduled task logs the current number of materialized graphs
   * and cache statistics (if enabled).
   */
  @Scheduled(fixedRate = 300000)  // 5 minutes
  public void logStatistics() {
    if (!properties.isPeriodicLoggingEnabled()) {
      return;
    }

    int graphCount = materializedBranchRepo.getGraphCount();
    int maxBranches = properties.getMaxBranches();

    logger.info("Materialized Views Stats: {} graphs in cache (max: {})",
        graphCount, maxBranches);

    // Log cache statistics if enabled
    if (properties.isCacheStatsEnabled()) {
      CacheStats stats = materializedBranchRepo.getCacheStats();

      final double percentMultiplier = 100.0;
      final double nanosToMillis = 1_000_000.0;

      logger.info("Cache Stats: hits={}, misses={}, evictions={}, hit_rate={:.2f}%, "
              + "avg_load_time={:.2f}ms",
          stats.hitCount(),
          stats.missCount(),
          stats.evictionCount(),
          stats.hitRate() * percentMultiplier,
          stats.averageLoadPenalty() / nanosToMillis
      );
    }
  }
}
