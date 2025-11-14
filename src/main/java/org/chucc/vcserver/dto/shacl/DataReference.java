package org.chucc.vcserver.dto.shacl;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.chucc.vcserver.exception.InvalidGraphReferenceException;

/**
 * Reference to data graphs to be validated.
 *
 * <p>Supports two source types:</p>
 * <ul>
 *   <li><b>local</b> - Graphs from local dataset (requires: dataset, graphs)</li>
 *   <li><b>remote</b> - Graphs from remote SPARQL endpoint (requires: endpoint, graphs)</li>
 * </ul>
 *
 * <p><b>Version control selectors</b> (optional, mutually exclusive):</p>
 * <ul>
 *   <li>branch - Validate against branch HEAD</li>
 *   <li>commit - Validate against specific commit</li>
 *   <li>asOf - Validate against state at timestamp</li>
 * </ul>
 *
 * @param source data source type
 * @param dataset local dataset name (for source=local)
 * @param graphs list of graph URIs (or "default", "union")
 * @param endpoint remote SPARQL endpoint URL (for source=remote)
 * @param branch branch name selector (optional)
 * @param commit commit ID selector (optional)
 * @param asOf RFC3339 timestamp selector (optional)
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP",
    justification = "Returns unmodifiable list created in constructor")
public record DataReference(
    @NotBlank(message = "source is required")
    @Pattern(regexp = "local|remote", message = "source must be 'local' or 'remote'")
    String source,

    @Pattern(regexp = "^[A-Za-z0-9._-]{1,249}$", message = "Invalid dataset name")
    String dataset,

    @NotEmpty(message = "graphs list cannot be empty")
    List<String> graphs,

    String endpoint,

    @Pattern(regexp = "^[A-Za-z0-9._-]{1,255}$", message = "Invalid branch name")
    String branch,

    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}$",
             message = "Invalid commit ID (must be UUIDv7)")
    String commit,

    String asOf
) {

  /**
   * Compact constructor with cross-field validation.
   */
  public DataReference {
    // Defensive copy to prevent external modification
    graphs = graphs != null
        ? Collections.unmodifiableList(new ArrayList<>(graphs))
        : List.of();

    // Validate field combinations based on source
    validateFieldCombinations(source, dataset, endpoint);

    // Validate selector conflicts
    validateSelectors(branch, commit, asOf);
  }

  /**
   * Validate required fields for each source type.
   *
   * @param source data source type
   * @param dataset dataset name
   * @param endpoint SPARQL endpoint URL
   * @throws InvalidGraphReferenceException if field combination is invalid
   */
  private static void validateFieldCombinations(
      String source,
      String dataset,
      String endpoint
  ) {
    switch (source) {
      case "local":
        if (dataset == null) {
          throw new InvalidGraphReferenceException(
              "source='local' requires 'dataset' field"
          );
        }
        if (endpoint != null) {
          throw new InvalidGraphReferenceException(
              "source='local' must not have 'endpoint' field"
          );
        }
        break;

      case "remote":
        if (endpoint == null) {
          throw new InvalidGraphReferenceException(
              "source='remote' requires 'endpoint' field"
          );
        }
        if (dataset != null) {
          throw new InvalidGraphReferenceException(
              "source='remote' must not have 'dataset' field"
          );
        }
        break;

      default:
        throw new InvalidGraphReferenceException(
            "source must be 'local' or 'remote'"
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
