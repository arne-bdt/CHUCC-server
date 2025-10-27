package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.core.DatasetGraph;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.exception.DatasetNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.chucc.vcserver.util.MaterializedGraphBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for rebuilding materialized views from event log.
 *
 * <p>This service provides recovery mechanisms when materialized graphs
 * become corrupted or out of sync with the commit history. It rebuilds
 * the materialized graph by replaying all commits from the branch's
 * history in order.
 *
 * <p><b>CQRS Architecture Note:</b> This service queries both write-side
 * (CommitRepository - event log) and read-side (BranchRepository,
 * MaterializedBranchRepository) repositories. It also directly mutates the
 * MaterializedBranchRepository without creating events. This cross-concern
 * access and CQRS bypass is acceptable for recovery operations because:
 * <ul>
 *   <li>Rebuilds are manual administrative operations (not normal writes)</li>
 *   <li>CommitRepository is the source of truth (authoritative event log)</li>
 *   <li>The rebuild reconstructs read model FROM write model (event replay)</li>
 *   <li>The final state matches what normal projection would produce</li>
 *   <li>Rebuilds are idempotent and can be repeated if interrupted</li>
 *   <li>No events are created because rebuild restores state from existing events</li>
 * </ul>
 *
 * <p><b>Warning:</b> Do NOT use this pattern for normal write operations.
 * Normal writes MUST go through the CommandHandler → Event → Projector flow.
 * See {@code docs/architecture/cqrs-event-sourcing.md} for details.
 *
 * <p><b>Rebuild Process:</b>
 * <ol>
 *   <li>Validate dataset and branch exist</li>
 *   <li>Build commit chain from root to HEAD</li>
 *   <li>Create new empty graph</li>
 *   <li>Apply all patches from commit history in order</li>
 *   <li>Replace existing materialized graph atomically</li>
 * </ol>
 *
 * <p>Metrics are recorded for rebuild operations:
 * <ul>
 *   <li>{@code chucc.materialized_views.rebuild.total} - Counter</li>
 *   <li>{@code chucc.materialized_views.rebuild.duration} - Timer</li>
 * </ul>
 */
@Service
public class MaterializedViewRebuildService {

  private static final Logger logger =
      LoggerFactory.getLogger(MaterializedViewRebuildService.class);

  private final MaterializedBranchRepository materializedBranchRepo;
  private final CommitRepository commitRepository;
  private final BranchRepository branchRepository;
  private final MeterRegistry meterRegistry;

  /**
   * Constructs the rebuild service.
   *
   * @param materializedBranchRepo repository for materialized graphs
   * @param commitRepository repository for commit history
   * @param branchRepository repository for branch metadata
   * @param meterRegistry metrics registry
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "All dependencies are Spring-managed beans and are intentionally shared")
  public MaterializedViewRebuildService(
      MaterializedBranchRepository materializedBranchRepo,
      CommitRepository commitRepository,
      BranchRepository branchRepository,
      MeterRegistry meterRegistry) {
    this.materializedBranchRepo = materializedBranchRepo;
    this.commitRepository = commitRepository;
    this.branchRepository = branchRepository;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Rebuild materialized graph for a branch from commit history.
   *
   * <p>This operation:
   * <ol>
   *   <li>Validates dataset and branch exist</li>
   *   <li>Uses MaterializedGraphBuilder to rebuild from commit history</li>
   *   <li>Replaces existing materialized graph atomically</li>
   * </ol>
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @return rebuild statistics
   * @throws DatasetNotFoundException if dataset doesn't exist
   * @throws BranchNotFoundException if branch doesn't exist
   */
  @SuppressFBWarnings(
      value = "REC_CATCH_EXCEPTION",
      justification = "Catch all exceptions to record metrics")
  public RebuildResult rebuildBranch(String dataset, String branch) {
    logger.info("Starting rebuild of materialized graph for {}/{}", dataset, branch);

    Timer.Sample sample = Timer.start(meterRegistry);

    try {
      // 1. Validate dataset exists (by checking if it has any branches)
      if (branchRepository.findAllByDataset(dataset).isEmpty()) {
        throw new DatasetNotFoundException("Dataset not found: " + dataset);
      }

      // 2. Validate branch exists
      if (!branchRepository.exists(dataset, branch)) {
        throw new BranchNotFoundException(branch);
      }

      // 3. Use MaterializedGraphBuilder to rebuild from commit history
      DatasetGraph newGraph = MaterializedGraphBuilder.rebuildFromCommitHistory(
          commitRepository, branchRepository, dataset, branch);

      // 4. Replace existing graph atomically
      // (In current implementation, we can't truly atomic replace, but we minimize the window)
      materializedBranchRepo.deleteBranch(dataset, branch);
      materializedBranchRepo.createBranch(dataset, branch, Optional.empty());

      // Copy new graph to materialized repository
      DatasetGraph targetGraph = materializedBranchRepo.getBranchGraph(dataset, branch);
      copyGraph(newGraph, targetGraph);

      // Close temporary graph
      newGraph.close();

      // 5. Record metrics
      long durationMs = sample.stop(meterRegistry.timer("chucc.materialized_views.rebuild.duration",
          "dataset", dataset,
          "branch", branch,
          "status", "success"
      ));

      meterRegistry.counter("chucc.materialized_views.rebuild.total",
          "dataset", dataset,
          "branch", branch,
          "status", "success"
      ).increment();

      // Count commits for logging
      final int commitCount = countCommitsInBranch(dataset, branch);
      logger.info("Successfully rebuilt materialized graph for {}/{} ({} commits)",
          dataset, branch, commitCount);

      return new RebuildResult(commitCount, durationMs);

    } catch (Exception e) {
      sample.stop(meterRegistry.timer("chucc.materialized_views.rebuild.duration",
          "dataset", dataset,
          "branch", branch,
          "status", "error"
      ));

      meterRegistry.counter("chucc.materialized_views.rebuild.total",
          "dataset", dataset,
          "branch", branch,
          "status", "error"
      ).increment();

      logger.error("Failed to rebuild materialized graph for {}/{}", dataset, branch, e);
      throw e;
    }
  }

  /**
   * Count commits in a branch for metrics.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @return number of commits in the branch
   */
  private int countCommitsInBranch(String dataset, String branch) {
    return branchRepository.findByDatasetAndName(dataset, branch)
        .map(b -> b.getCommitCount())
        .orElse(0);
  }

  /**
   * Copy all data from source graph to target graph.
   *
   * <p>This copies:
   * <ul>
   *   <li>All named graphs</li>
   *   <li>Default graph</li>
   *   <li>Prefix mappings</li>
   * </ul>
   *
   * @param source the source graph
   * @param target the target graph
   */
  private void copyGraph(DatasetGraph source, DatasetGraph target) {
    source.begin(ReadWrite.READ);
    target.begin(ReadWrite.WRITE);
    try {
      // Copy all named graphs
      source.listGraphNodes().forEachRemaining(graphName -> {
        org.apache.jena.graph.Graph sourceGraph = source.getGraph(graphName);
        org.apache.jena.graph.Graph targetGraph = target.getGraph(graphName);

        targetGraph.getPrefixMapping().setNsPrefixes(sourceGraph.getPrefixMapping());
        sourceGraph.find().forEachRemaining(targetGraph::add);
      });

      // Copy default graph
      org.apache.jena.graph.Graph defaultSource = source.getDefaultGraph();
      org.apache.jena.graph.Graph defaultTarget = target.getDefaultGraph();
      defaultTarget.getPrefixMapping().setNsPrefixes(defaultSource.getPrefixMapping());
      defaultSource.find().forEachRemaining(defaultTarget::add);

      source.commit();
      target.commit();
    } finally {
      source.end();
      target.end();
    }
  }

  /**
   * Result of a rebuild operation.
   *
   * @param commitsProcessed number of commits processed
   * @param durationMs rebuild duration in milliseconds
   */
  public record RebuildResult(int commitsProcessed, long durationMs) {}
}
