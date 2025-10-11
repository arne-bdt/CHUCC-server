package org.chucc.vcserver.domain;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.Objects;
import org.apache.jena.sparql.core.DatasetGraph;

/**
 * Domain entity representing a snapshot of a dataset at a specific commit.
 * Snapshots are used to speed up recovery by loading a checkpoint instead of
 * replaying all events from the beginning.
 *
 * <p>A snapshot contains the complete materialized state of the dataset at the
 * specified commit, allowing the system to skip all events before that commit
 * during recovery.
 *
 * <p><strong>Note:</strong> The DatasetGraph is stored and returned by reference
 * for performance reasons. Callers should treat the graph as read-only.
 */
public record Snapshot(
    CommitId commitId,
    String branchName,
    Instant timestamp,
    DatasetGraph graph) {

  /**
   * Creates a new Snapshot with validation.
   *
   * @param commitId the commit ID at which the snapshot was taken (must be non-null)
   * @param branchName the branch name (must be non-null and non-blank)
   * @param timestamp the snapshot creation timestamp (must be non-null)
   * @param graph the materialized dataset graph (must be non-null)
   * @throws IllegalArgumentException if any validation fails
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "DatasetGraph is stored by reference for performance. "
          + "Snapshots are internal objects and the graph is treated as read-only.")
  public Snapshot {
    Objects.requireNonNull(commitId, "Commit ID cannot be null");
    Objects.requireNonNull(branchName, "Branch name cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");
    Objects.requireNonNull(graph, "Dataset graph cannot be null");

    if (branchName.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
  }

  /**
   * Returns the dataset graph.
   * The returned graph should be treated as read-only.
   *
   * @return the dataset graph
   */
  @Override
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "DatasetGraph is returned by reference for performance. "
          + "Snapshots are internal objects and the graph is treated as read-only.")
  public DatasetGraph graph() {
    return graph;
  }
}
