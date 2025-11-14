package org.chucc.vcserver.dto.shacl;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.stream.Stream;
import org.chucc.vcserver.exception.InvalidGraphReferenceException;

/**
 * Reference to a graph (local, remote, or inline).
 *
 * <p>Supports three source types:</p>
 * <ul>
 *   <li><b>inline</b> - RDF data embedded in request (requires: data)</li>
 *   <li><b>local</b> - Graph from local dataset (requires: dataset, graph)</li>
 *   <li><b>remote</b> - Graph from remote SPARQL endpoint (requires: endpoint, graph)</li>
 * </ul>
 *
 * <p><b>Version control selectors</b> (optional, mutually exclusive):</p>
 * <ul>
 *   <li>branch - Validate against branch HEAD</li>
 *   <li>commit - Validate against specific commit</li>
 *   <li>asOf - Validate against state at timestamp</li>
 * </ul>
 *
 * @param source graph source type
 * @param format RDF serialization format (default: turtle)
 * @param dataset local dataset name (for source=local)
 * @param graph graph URI, "default", or "union" (for source=local/remote)
 * @param endpoint remote SPARQL endpoint URL (for source=remote)
 * @param data inline RDF content (for source=inline)
 * @param branch branch name selector (optional)
 * @param commit commit ID selector (optional)
 * @param asOf RFC3339 timestamp selector (optional)
 */
public record GraphReference(
    @NotBlank(message = "source is required")
    @Pattern(regexp = "inline|local|remote",
        message = "source must be 'inline', 'local', or 'remote'")
    String source,

    @Pattern(regexp = "turtle|jsonld|rdfxml|ntriples|n3",
             message = "format must be 'turtle', 'jsonld', 'rdfxml', 'ntriples', or 'n3'")
    String format,

    @Pattern(regexp = "^[A-Za-z0-9._-]{1,249}$", message = "Invalid dataset name")
    String dataset,

    String graph,  // URI, "default", or "union"

    String endpoint,  // Remote SPARQL endpoint URL

    String data,  // Inline RDF content

    @Pattern(regexp = "^[A-Za-z0-9._-]{1,255}$", message = "Invalid branch name")
    String branch,

    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}$",
             message = "Invalid commit ID (must be UUIDv7)")
    String commit,

    String asOf  // RFC3339 timestamp
) {

  /**
   * Compact constructor with cross-field validation.
   */
  public GraphReference {
    // Set default format
    if (format == null) {
      format = "turtle";
    }

    // Validate field combinations based on source
    validateFieldCombinations(source, dataset, graph, endpoint, data);

    // Validate selector conflicts
    validateSelectors(branch, commit, asOf);
  }

  /**
   * Validate required fields for each source type.
   *
   * @param source graph source type
   * @param dataset dataset name
   * @param graph graph URI
   * @param endpoint SPARQL endpoint URL
   * @param data inline RDF content
   * @throws InvalidGraphReferenceException if field combination is invalid
   */
  private static void validateFieldCombinations(
      String source,
      String dataset,
      String graph,
      String endpoint,
      String data
  ) {
    switch (source) {
      case "inline":
        if (data == null || data.isBlank()) {
          throw new InvalidGraphReferenceException(
              "source='inline' requires 'data' field"
          );
        }
        if (dataset != null || endpoint != null) {
          throw new InvalidGraphReferenceException(
              "source='inline' must not have 'dataset' or 'endpoint' fields"
          );
        }
        break;

      case "local":
        if (dataset == null || graph == null) {
          throw new InvalidGraphReferenceException(
              "source='local' requires 'dataset' and 'graph' fields"
          );
        }
        if (data != null || endpoint != null) {
          throw new InvalidGraphReferenceException(
              "source='local' must not have 'data' or 'endpoint' fields"
          );
        }
        break;

      case "remote":
        if (endpoint == null || graph == null) {
          throw new InvalidGraphReferenceException(
              "source='remote' requires 'endpoint' and 'graph' fields"
          );
        }
        if (data != null || dataset != null) {
          throw new InvalidGraphReferenceException(
              "source='remote' must not have 'data' or 'dataset' fields"
          );
        }
        break;

      default:
        throw new InvalidGraphReferenceException(
            "source must be 'inline', 'local', or 'remote'"
        );
    }
  }

  /**
   * Validate that selectors are mutually exclusive.
   *
   * @param branch branch name selector
   * @param commit commit ID selector
   * @param asOf timestamp selector
   * @throws InvalidGraphReferenceException if multiple selectors provided
   */
  private static void validateSelectors(String branch, String commit, String asOf) {
    long selectorCount = Stream.of(branch, commit, asOf)
        .filter(s -> s != null && !s.isBlank())
        .count();

    if (selectorCount > 1) {
      throw new InvalidGraphReferenceException(
          "Only one selector allowed: branch, commit, or asOf"
      );
    }
  }
}
