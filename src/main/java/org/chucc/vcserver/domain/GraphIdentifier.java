package org.chucc.vcserver.domain;

import java.util.Objects;
import org.apache.jena.irix.IRIException;
import org.apache.jena.irix.IRIx;

/**
 * Value object representing a graph identifier in the Graph Store Protocol.
 * Can represent either the default graph or a named graph identified by an IRI.
 * Immutable and validates IRIs according to RFC 3986/3987.
 */
public final class GraphIdentifier {
  private static final GraphIdentifier DEFAULT_GRAPH = new GraphIdentifier(null);

  private final String iri;

  /**
   * Private constructor for creating graph identifiers.
   *
   * @param iri the IRI string, or null for default graph
   */
  private GraphIdentifier(String iri) {
    this.iri = iri;
  }

  /**
   * Creates a GraphIdentifier for the default graph.
   *
   * @return a GraphIdentifier representing the default graph
   */
  public static GraphIdentifier defaultGraph() {
    return DEFAULT_GRAPH;
  }

  /**
   * Creates a GraphIdentifier for a named graph with the specified IRI.
   *
   * @param iri the graph IRI (must be non-null, non-blank, and valid according to RFC 3986/3987)
   * @return a GraphIdentifier representing the named graph
   * @throws IllegalArgumentException if the IRI is null, blank, or invalid
   */
  public static GraphIdentifier named(String iri) {
    Objects.requireNonNull(iri, "IRI cannot be null");

    if (iri.isBlank()) {
      throw new IllegalArgumentException("IRI cannot be blank");
    }

    // Validate IRI using Apache Jena's IRI validation
    try {
      IRIx.create(iri);
    } catch (IRIException e) {
      throw new IllegalArgumentException("Invalid IRI: " + iri, e);
    }

    return new GraphIdentifier(iri);
  }

  /**
   * Checks if this identifier represents the default graph.
   *
   * @return true if this is the default graph
   */
  public boolean isDefault() {
    return iri == null;
  }

  /**
   * Checks if this identifier represents a named graph.
   *
   * @return true if this is a named graph
   */
  public boolean isNamed() {
    return iri != null;
  }

  /**
   * Gets the IRI of this named graph.
   *
   * @return the IRI string
   * @throws IllegalStateException if this is the default graph
   */
  public String iri() {
    if (isDefault()) {
      throw new IllegalStateException("Default graph has no IRI");
    }
    return iri;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    GraphIdentifier that = (GraphIdentifier) obj;
    return Objects.equals(iri, that.iri);
  }

  @Override
  public int hashCode() {
    return Objects.hash(iri);
  }

  @Override
  public String toString() {
    if (isDefault()) {
      return "GraphIdentifier{default}";
    }
    return "GraphIdentifier{iri='" + iri + "'}";
  }
}
