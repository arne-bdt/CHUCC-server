package org.chucc.vcserver.health;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.chucc.vcserver.repository.CommitRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for repository accessibility.
 * Checks if commit repository is accessible.
 */
@Component
public class RepositoryHealthIndicator implements HealthIndicator {

  private final CommitRepository commitRepository;

  /**
   * Creates a new repository health indicator.
   *
   * @param commitRepository the commit repository
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "CommitRepository is a Spring-managed bean and is intentionally shared"
  )
  public RepositoryHealthIndicator(CommitRepository commitRepository) {
    this.commitRepository = commitRepository;
  }

  /**
   * Checks repository health.
   *
   * @return health status
   */
  @Override
  public Health health() {
    try {
      // Simple check - if this works, repository is accessible
      // Try to access the repository (returns empty list if no commits)
      commitRepository.findAllByDataset("default");
      return Health.up()
          .withDetail("status", "Repositories accessible")
          .build();
    } catch (Exception e) {
      return Health.down()
          .withDetail("error", e.getMessage())
          .withException(e)
          .build();
    }
  }
}
