package org.chucc.vcserver.controller.util;

import org.springframework.http.HttpHeaders;

/**
 * Utility class for building response headers for semantic routing.
 *
 * <p>Provides helper methods to add Content-Location and Link headers that help clients
 * discover canonical URLs and related resources (HATEOAS pattern).
 */
public final class ResponseHeaderBuilder {

  private ResponseHeaderBuilder() {
    // Utility class - prevent instantiation
  }

  /**
   * Adds Content-Location header with canonical URL.
   *
   * @param headers HTTP headers
   * @param canonicalUrl Canonical URL path
   */
  public static void addContentLocation(HttpHeaders headers, String canonicalUrl) {
    headers.set("Content-Location", canonicalUrl);
  }

  /**
   * Adds Link header for related resource.
   *
   * @param headers HTTP headers
   * @param url Related resource URL
   * @param rel Relationship type (e.g., "version", "branch", "commit")
   */
  public static void addLink(HttpHeaders headers, String url, String rel) {
    String linkValue = String.format("<%s>; rel=\"%s\"", url, rel);
    headers.add("Link", linkValue);
  }

  /**
   * Adds Link header for commit version.
   *
   * @param headers HTTP headers
   * @param dataset Dataset name
   * @param commitId Commit ID
   */
  public static void addCommitLink(HttpHeaders headers, String dataset, String commitId) {
    addLink(headers, VersionControlUrls.commit(dataset, commitId), "version");
  }

  /**
   * Adds Link header for branch.
   *
   * @param headers HTTP headers
   * @param dataset Dataset name
   * @param branch Branch name
   */
  public static void addBranchLink(HttpHeaders headers, String dataset, String branch) {
    addLink(headers, VersionControlUrls.branch(dataset, branch), "branch");
  }

  /**
   * Adds Link header for tag.
   *
   * @param headers HTTP headers
   * @param dataset Dataset name
   * @param tag Tag name
   */
  public static void addTagLink(HttpHeaders headers, String dataset, String tag) {
    addLink(headers, VersionControlUrls.tag(dataset, tag), "tag");
  }
}
