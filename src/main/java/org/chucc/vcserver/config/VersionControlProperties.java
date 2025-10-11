package org.chucc.vcserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for SPARQL Version Control conformance levels.
 *
 * <p>Level 1 (Basic): Commits (UUIDv7), branches, history, query at branch/commit,
 * RDF Patch support, strong ETags, problem+json.
 *
 * <p>Level 2 (Advanced): Three-way merge, conflict detection/representation,
 * fast-forward, revert, reset, tags, cherry-pick, blame.
 */
@Component
@ConfigurationProperties(prefix = "vc")
public class VersionControlProperties {

  /**
   * Conformance level: 1 (Basic) or 2 (Advanced).
   * Default is Level 2 (full feature set).
   */
  private int level = 2;

  /**
   * Whether to enable commit operations.
   * Part of Level 1.
   */
  private boolean commitsEnabled = true;

  /**
   * Whether to enable branch operations.
   * Part of Level 1.
   */
  private boolean branchesEnabled = true;

  /**
   * Whether to enable history operations.
   * Part of Level 1.
   */
  private boolean historyEnabled = true;

  /**
   * Whether to enable RDF Patch support.
   * Part of Level 1.
   */
  private boolean rdfPatchEnabled = true;

  /**
   * Whether to enable three-way merge.
   * Part of Level 2.
   */
  private boolean mergeEnabled = true;

  /**
   * Whether to enable conflict detection and representation.
   * Part of Level 2.
   */
  private boolean conflictDetectionEnabled = true;

  /**
   * Whether to enable tag operations.
   * Part of Level 2.
   */
  private boolean tagsEnabled = true;

  /**
   * Whether to allow tag deletion.
   * Default is true (deletion allowed).
   */
  private boolean tagDeletionAllowed = true;

  /**
   * Whether to enable revert operations.
   * Part of Level 2.
   */
  private boolean revertEnabled = true;

  /**
   * Whether to enable reset operations.
   * Part of Level 2.
   */
  private boolean resetEnabled = true;

  /**
   * Whether to enable cherry-pick operations.
   * Part of Level 2.
   */
  private boolean cherryPickEnabled = true;

  /**
   * Whether to enable blame/annotate operations.
   * Part of Level 2.
   */
  private boolean blameEnabled = true;

  /**
   * Whether to enable diff endpoint (extension).
   * This is not part of the official SPARQL 1.2 Protocol specification.
   */
  private boolean diffEnabled = true;

  /**
   * Whether to enable automatic snapshot creation.
   */
  private boolean snapshotsEnabled = true;

  /**
   * Number of commits between automatic snapshots.
   * Default is 100 commits.
   */
  private int snapshotInterval = 100;

  /**
   * Whether to allow Kafka topic deletion when deleting datasets.
   * Default is false (keep topics for audit trail).
   */
  private boolean allowKafkaTopicDeletion = false;

  /**
   * Gets the conformance level.
   *
   * @return conformance level (1 or 2)
   */
  public int getLevel() {
    return level;
  }

  /**
   * Sets the conformance level and auto-disables Level 2 features if set to 1.
   *
   * @param level conformance level (1 or 2)
   * @throws IllegalArgumentException if level is not 1 or 2
   */
  public void setLevel(int level) {
    if (level < 1 || level > 2) {
      throw new IllegalArgumentException("Version control level must be 1 or 2");
    }
    this.level = level;
    // Auto-disable Level 2 features if level is set to 1
    if (level == 1) {
      this.mergeEnabled = false;
      this.conflictDetectionEnabled = false;
      this.tagsEnabled = false;
      this.revertEnabled = false;
      this.resetEnabled = false;
      this.cherryPickEnabled = false;
      this.blameEnabled = false;
    }
  }

  public boolean isCommitsEnabled() {
    return commitsEnabled && level >= 1;
  }

  public void setCommitsEnabled(boolean commitsEnabled) {
    this.commitsEnabled = commitsEnabled;
  }

  public boolean isBranchesEnabled() {
    return branchesEnabled && level >= 1;
  }

  public void setBranchesEnabled(boolean branchesEnabled) {
    this.branchesEnabled = branchesEnabled;
  }

  public boolean isHistoryEnabled() {
    return historyEnabled && level >= 1;
  }

  public void setHistoryEnabled(boolean historyEnabled) {
    this.historyEnabled = historyEnabled;
  }

  public boolean isRdfPatchEnabled() {
    return rdfPatchEnabled && level >= 1;
  }

  public void setRdfPatchEnabled(boolean rdfPatchEnabled) {
    this.rdfPatchEnabled = rdfPatchEnabled;
  }

  public boolean isMergeEnabled() {
    return mergeEnabled && level >= 2;
  }

  public void setMergeEnabled(boolean mergeEnabled) {
    this.mergeEnabled = mergeEnabled;
  }

  public boolean isConflictDetectionEnabled() {
    return conflictDetectionEnabled && level >= 2;
  }

  public void setConflictDetectionEnabled(boolean conflictDetectionEnabled) {
    this.conflictDetectionEnabled = conflictDetectionEnabled;
  }

  public boolean isTagsEnabled() {
    return tagsEnabled && level >= 2;
  }

  public void setTagsEnabled(boolean tagsEnabled) {
    this.tagsEnabled = tagsEnabled;
  }

  public boolean isRevertEnabled() {
    return revertEnabled && level >= 2;
  }

  public void setRevertEnabled(boolean revertEnabled) {
    this.revertEnabled = revertEnabled;
  }

  public boolean isResetEnabled() {
    return resetEnabled && level >= 2;
  }

  public void setResetEnabled(boolean resetEnabled) {
    this.resetEnabled = resetEnabled;
  }

  public boolean isCherryPickEnabled() {
    return cherryPickEnabled && level >= 2;
  }

  public void setCherryPickEnabled(boolean cherryPickEnabled) {
    this.cherryPickEnabled = cherryPickEnabled;
  }

  public boolean isBlameEnabled() {
    return blameEnabled && level >= 2;
  }

  public void setBlameEnabled(boolean blameEnabled) {
    this.blameEnabled = blameEnabled;
  }

  /**
   * Checks if diff endpoint is enabled.
   *
   * @return true if diff endpoint is enabled
   */
  public boolean isDiffEnabled() {
    return diffEnabled;
  }

  /**
   * Sets whether diff endpoint is enabled.
   *
   * @param diffEnabled true to enable diff endpoint
   */
  public void setDiffEnabled(boolean diffEnabled) {
    this.diffEnabled = diffEnabled;
  }

  /**
   * Checks if automatic snapshot creation is enabled.
   *
   * @return true if snapshots are enabled
   */
  public boolean isSnapshotsEnabled() {
    return snapshotsEnabled;
  }

  /**
   * Sets whether automatic snapshot creation is enabled.
   *
   * @param snapshotsEnabled true to enable snapshots
   */
  public void setSnapshotsEnabled(boolean snapshotsEnabled) {
    this.snapshotsEnabled = snapshotsEnabled;
  }

  /**
   * Gets the snapshot interval (number of commits between snapshots).
   *
   * @return the snapshot interval
   */
  public int getSnapshotInterval() {
    return snapshotInterval;
  }

  /**
   * Sets the snapshot interval (number of commits between snapshots).
   *
   * @param snapshotInterval the snapshot interval (must be positive)
   * @throws IllegalArgumentException if interval is not positive
   */
  public void setSnapshotInterval(int snapshotInterval) {
    if (snapshotInterval <= 0) {
      throw new IllegalArgumentException("Snapshot interval must be positive");
    }
    this.snapshotInterval = snapshotInterval;
  }

  /**
   * Checks if tag deletion is allowed.
   *
   * @return true if tag deletion is allowed
   */
  public boolean isTagDeletionAllowed() {
    return tagDeletionAllowed;
  }

  /**
   * Sets whether tag deletion is allowed.
   *
   * @param tagDeletionAllowed true to allow tag deletion
   */
  public void setTagDeletionAllowed(boolean tagDeletionAllowed) {
    this.tagDeletionAllowed = tagDeletionAllowed;
  }

  /**
   * Checks if Kafka topic deletion is allowed.
   *
   * @return true if Kafka topic deletion is allowed
   */
  public boolean isAllowKafkaTopicDeletion() {
    return allowKafkaTopicDeletion;
  }

  /**
   * Sets whether Kafka topic deletion is allowed.
   *
   * @param allowKafkaTopicDeletion true to allow Kafka topic deletion
   */
  public void setAllowKafkaTopicDeletion(boolean allowKafkaTopicDeletion) {
    this.allowKafkaTopicDeletion = allowKafkaTopicDeletion;
  }
}
