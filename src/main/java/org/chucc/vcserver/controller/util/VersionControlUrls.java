package org.chucc.vcserver.controller.util;

import org.chucc.vcserver.domain.CommitId;

/**
 * Utility class for constructing version control URLs following semantic routing patterns.
 *
 * <p>This class provides helper methods to build RESTful URLs that make datasets, branches,
 * commits, and tags directly shareable and bookmarkable.
 *
 * <p>URL Pattern: {@code /{dataset}/version/{ref-type}/{ref-name}/{service}}
 *
 * @see <a href="docs/architecture/semantic-routing.md">Semantic Routing Specification</a>
 */
public final class VersionControlUrls {

  private VersionControlUrls() {
    // Utility class - prevent instantiation
  }

  // ==================== Dataset URLs ====================

  /**
   * Constructs URL for dataset root.
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/mydata")
   */
  public static String dataset(String dataset) {
    return "/" + dataset;
  }

  /**
   * Constructs URL for dataset metadata endpoint.
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/datasets/mydata")
   */
  public static String datasetMetadata(String dataset) {
    return "/datasets/" + dataset;
  }

  // ==================== Branch URLs ====================

  /**
   * Constructs URL for branch resource.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main")
   */
  public static String branch(String dataset, String branch) {
    return String.format("/%s/version/branches/%s", dataset, branch);
  }

  /**
   * Constructs URL for branch list endpoint.
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/mydata/version/branches")
   */
  public static String branches(String dataset) {
    return String.format("/%s/version/branches", dataset);
  }

  /**
   * Constructs URL for branch history.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/history")
   */
  public static String branchHistory(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/history", dataset, branch);
  }

  // ==================== Commit URLs ====================

  /**
   * Constructs URL for commit resource.
   *
   * @param dataset Dataset name
   * @param commitId Commit ID
   * @return URL path (e.g., "/mydata/version/commits/01936d8f-1234-7890-abcd-ef1234567890")
   */
  public static String commit(String dataset, String commitId) {
    return String.format("/%s/version/commits/%s", dataset, commitId);
  }

  /**
   * Constructs URL for commit resource.
   *
   * @param dataset Dataset name
   * @param commitId Commit ID object
   * @return URL path
   */
  public static String commit(String dataset, CommitId commitId) {
    return commit(dataset, commitId.value());
  }

  /**
   * Constructs URL for commit list endpoint.
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/mydata/version/commits")
   */
  public static String commits(String dataset) {
    return String.format("/%s/version/commits", dataset);
  }

  /**
   * Constructs URL for commit history (from specific commit).
   *
   * @param dataset Dataset name
   * @param commitId Commit ID
   * @return URL path (e.g., "/mydata/version/commits/01936d8f.../history")
   */
  public static String commitHistory(String dataset, String commitId) {
    return String.format("/%s/version/commits/%s/history", dataset, commitId);
  }

  /**
   * Constructs URL for diff between two commits.
   *
   * @param dataset Dataset name
   * @param fromCommit Source commit ID
   * @param toCommit Target commit ID
   * @return URL path (e.g., "/mydata/version/commits/abc/diff/def")
   */
  public static String commitDiff(String dataset, String fromCommit, String toCommit) {
    return String.format("/%s/version/commits/%s/diff/%s", dataset, fromCommit, toCommit);
  }

  // ==================== Tag URLs ====================

  /**
   * Constructs URL for tag resource.
   *
   * @param dataset Dataset name
   * @param tag Tag name
   * @return URL path (e.g., "/mydata/version/tags/v1.0")
   */
  public static String tag(String dataset, String tag) {
    return String.format("/%s/version/tags/%s", dataset, tag);
  }

  /**
   * Constructs URL for tag list endpoint.
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/mydata/version/tags")
   */
  public static String tags(String dataset) {
    return String.format("/%s/version/tags", dataset);
  }

  // ==================== SPARQL Endpoints ====================

  /**
   * Constructs URL for SPARQL query endpoint at branch.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/sparql")
   */
  public static String sparqlAtBranch(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/sparql", dataset, branch);
  }

  /**
   * Constructs URL for SPARQL query endpoint at commit.
   *
   * @param dataset Dataset name
   * @param commitId Commit ID
   * @return URL path (e.g., "/mydata/version/commits/01936d8f.../sparql")
   */
  public static String sparqlAtCommit(String dataset, String commitId) {
    return String.format("/%s/version/commits/%s/sparql", dataset, commitId);
  }

  /**
   * Constructs URL for SPARQL query endpoint at tag.
   *
   * @param dataset Dataset name
   * @param tag Tag name
   * @return URL path (e.g., "/mydata/version/tags/v1.0/sparql")
   */
  public static String sparqlAtTag(String dataset, String tag) {
    return String.format("/%s/version/tags/%s/sparql", dataset, tag);
  }

  /**
   * Constructs URL for SPARQL update endpoint at branch.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/update")
   */
  public static String updateAtBranch(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/update", dataset, branch);
  }

  /**
   * Constructs URL for current-state SPARQL query (shortcut to main HEAD).
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/mydata/sparql")
   */
  public static String sparql(String dataset) {
    return String.format("/%s/sparql", dataset);
  }

  /**
   * Constructs URL for current-state SPARQL update (shortcut to main branch).
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/mydata/update")
   */
  public static String update(String dataset) {
    return String.format("/%s/update", dataset);
  }

  // ==================== Graph Store Protocol Endpoints ====================

  /**
   * Constructs URL for GSP endpoint at branch.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/data")
   */
  public static String dataAtBranch(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/data", dataset, branch);
  }

  /**
   * Constructs URL for GSP endpoint at commit.
   *
   * @param dataset Dataset name
   * @param commitId Commit ID
   * @return URL path (e.g., "/mydata/version/commits/01936d8f.../data")
   */
  public static String dataAtCommit(String dataset, String commitId) {
    return String.format("/%s/version/commits/%s/data", dataset, commitId);
  }

  /**
   * Constructs URL for GSP endpoint at tag.
   *
   * @param dataset Dataset name
   * @param tag Tag name
   * @return URL path (e.g., "/mydata/version/tags/v1.0/data")
   */
  public static String dataAtTag(String dataset, String tag) {
    return String.format("/%s/version/tags/%s/data", dataset, tag);
  }

  /**
   * Constructs URL for current-state GSP (shortcut to main HEAD).
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/mydata/data")
   */
  public static String data(String dataset) {
    return String.format("/%s/data", dataset);
  }

  // ==================== Advanced Operations ====================

  /**
   * Constructs URL for merge endpoint.
   *
   * @param dataset Dataset name
   * @param targetBranch Target branch name
   * @return URL path (e.g., "/mydata/version/branches/main/merge")
   */
  public static String merge(String dataset, String targetBranch) {
    return String.format("/%s/version/branches/%s/merge", dataset, targetBranch);
  }

  /**
   * Constructs URL for reset endpoint.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/reset")
   */
  public static String reset(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/reset", dataset, branch);
  }

  /**
   * Constructs URL for revert endpoint.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/revert")
   */
  public static String revert(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/revert", dataset, branch);
  }

  /**
   * Constructs URL for cherry-pick endpoint.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/cherry-pick")
   */
  public static String cherryPick(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/cherry-pick", dataset, branch);
  }

  /**
   * Constructs URL for rebase endpoint.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/rebase")
   */
  public static String rebase(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/rebase", dataset, branch);
  }

  /**
   * Constructs URL for squash endpoint.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/squash")
   */
  public static String squash(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/squash", dataset, branch);
  }

  // ==================== Batch Operations ====================

  /**
   * Constructs URL for batch graphs endpoint.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/batch-graphs")
   */
  public static String batchGraphs(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/batch-graphs", dataset, branch);
  }

  /**
   * Constructs URL for batch SPARQL endpoint.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/batch")
   */
  public static String batch(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/batch", dataset, branch);
  }

  // ==================== Refs ====================

  /**
   * Constructs URL for refs list (all branches and tags).
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/mydata/version/refs")
   */
  public static String refs(String dataset) {
    return String.format("/%s/version/refs", dataset);
  }
}
