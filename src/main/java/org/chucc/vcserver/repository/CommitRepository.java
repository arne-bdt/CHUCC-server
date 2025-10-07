package org.chucc.vcserver.repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.rdfpatch.RDFPatch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.springframework.stereotype.Repository;

/**
 * In-memory repository for managing commits and their associated RDF patches.
 * Thread-safe implementation using ConcurrentHashMap for concurrent read/write operations.
 */
@Repository
public class CommitRepository {
  private final Map<String, Map<CommitId, Commit>> datasetCommits = new ConcurrentHashMap<>();
  private final Map<String, Map<CommitId, RDFPatch>> datasetPatches = new ConcurrentHashMap<>();

  /**
   * Finds a commit by dataset and commit ID.
   *
   * @param datasetName the dataset name
   * @param commitId the commit ID
   * @return an Optional containing the commit if found, empty otherwise
   */
  public Optional<Commit> findByDatasetAndId(String datasetName, CommitId commitId) {
    return Optional.ofNullable(datasetCommits.get(datasetName))
        .map(commits -> commits.get(commitId));
  }

  /**
   * Finds an RDF patch by dataset and commit ID.
   *
   * @param datasetName the dataset name
   * @param commitId the commit ID
   * @return an Optional containing the RDF patch if found, empty otherwise
   */
  public Optional<RDFPatch> findPatchByDatasetAndId(String datasetName, CommitId commitId) {
    return Optional.ofNullable(datasetPatches.get(datasetName))
        .map(patches -> patches.get(commitId));
  }

  /**
   * Saves a commit and its associated RDF patch.
   *
   * @param datasetName the dataset name
   * @param commit the commit to save
   * @param patch the RDF patch associated with the commit
   * @return the saved commit
   */
  public Commit save(String datasetName, Commit commit, RDFPatch patch) {
    datasetCommits.computeIfAbsent(datasetName, k -> new ConcurrentHashMap<>())
        .put(commit.id(), commit);
    datasetPatches.computeIfAbsent(datasetName, k -> new ConcurrentHashMap<>())
        .put(commit.id(), patch);
    return commit;
  }

  /**
   * Finds all commits for a dataset.
   *
   * @param datasetName the dataset name
   * @return a list of all commits in the dataset
   */
  public List<Commit> findAllByDataset(String datasetName) {
    return Optional.ofNullable(datasetCommits.get(datasetName))
        .map(commits -> List.copyOf(commits.values()))
        .orElse(List.of());
  }

  /**
   * Checks if a commit exists.
   *
   * @param datasetName the dataset name
   * @param commitId the commit ID
   * @return true if the commit exists
   */
  public boolean exists(String datasetName, CommitId commitId) {
    return findByDatasetAndId(datasetName, commitId).isPresent();
  }

  /**
   * Deletes all commits and patches for a dataset.
   *
   * @param datasetName the dataset name
   */
  public void deleteAllByDataset(String datasetName) {
    datasetCommits.remove(datasetName);
    datasetPatches.remove(datasetName);
  }

  /**
   * Finds the most recent commit at or before the given timestamp.
   *
   * @param datasetName the dataset name
   * @param timestamp the timestamp threshold (inclusive)
   * @return an Optional containing the most recent commit at or before the timestamp,
   *     empty if no such commit exists
   */
  public Optional<Commit> findLatestBeforeTimestamp(String datasetName, Instant timestamp) {
    return Optional.ofNullable(datasetCommits.get(datasetName))
        .map(commits -> commits.values().stream()
            .filter(commit -> !commit.timestamp().isAfter(timestamp))
            .max(Comparator.comparing(Commit::timestamp))
        )
        .orElse(Optional.empty());
  }
}
