package org.chucc.vcserver.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Utility for validating SPARQL prefix names and IRIs.
 *
 * <p>Validation rules based on SPARQL 1.1 specification:
 * <ul>
 *   <li>Prefix names: Must match PN_PREFIX pattern from SPARQL grammar</li>
 *   <li>PN_PREFIX: [A-Za-z] ([A-Za-z0-9_-] | '.')* [A-Za-z0-9_-]</li>
 *   <li>Single character prefixes: [A-Za-z]</li>
 *   <li>IRIs: Must be absolute (have a scheme like http:, https:, etc.)</li>
 * </ul>
 *
 * @see <a href="https://www.w3.org/TR/sparql11-query/#rPN_PREFIX">
 *   SPARQL 1.1 Query Language - PN_PREFIX</a>
 */
public final class PrefixValidator {

  /**
   * Pattern for SPARQL PN_PREFIX.
   * Matches: [A-Za-z] followed by optional ([A-Za-z0-9_-] or '.') ending with [A-Za-z0-9_-]
   * Also accepts single letter prefixes.
   */
  private static final Pattern PREFIX_NAME_PATTERN =
      Pattern.compile("^[A-Za-z]([A-Za-z0-9_\\-]|\\.[A-Za-z0-9_\\-])*$");

  /**
   * Pattern for URI scheme (RFC 3986).
   * Scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )
   */
  private static final Pattern SCHEME_PATTERN =
      Pattern.compile("^[A-Za-z][A-Za-z0-9+.\\-]*:");

  private PrefixValidator() {
    // Utility class
  }

  /**
   * Validates a SPARQL prefix name according to PN_PREFIX pattern.
   *
   * @param prefixName the prefix name to validate
   * @throws IllegalArgumentException if validation fails
   * @throws NullPointerException if prefixName is null
   */
  public static void validatePrefixName(String prefixName) {
    Objects.requireNonNull(prefixName, "Prefix name cannot be null");

    if (prefixName.isEmpty()) {
      throw new IllegalArgumentException("Prefix name cannot be empty");
    }

    if (!PREFIX_NAME_PATTERN.matcher(prefixName).matches()) {
      throw new IllegalArgumentException(
          "Invalid prefix name: '" + prefixName + "'. "
              + "Prefix names must start with a letter [A-Za-z], "
              + "followed by letters, digits, underscores, or hyphens, "
              + "and cannot end with a dot.");
    }
  }

  /**
   * Validates that an IRI is absolute (has a scheme).
   *
   * @param iri the IRI to validate
   * @throws IllegalArgumentException if IRI is not absolute
   * @throws NullPointerException if iri is null
   */
  public static void validateAbsoluteIri(String iri) {
    Objects.requireNonNull(iri, "IRI cannot be null");

    if (iri.isEmpty()) {
      throw new IllegalArgumentException("IRI cannot be empty");
    }

    // Quick check: does it have a scheme?
    if (!SCHEME_PATTERN.matcher(iri).find()) {
      throw new IllegalArgumentException(
          "IRI must be absolute (must have a scheme like http:, https:, etc.): " + iri);
    }

    // Additional validation using Java's URI parser
    try {
      URI uri = new URI(iri);
      if (!uri.isAbsolute()) {
        throw new IllegalArgumentException("IRI must be absolute: " + iri);
      }
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid IRI syntax: " + iri, e);
    }
  }
}
