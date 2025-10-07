package org.chucc.vcserver.util;

import java.util.Locale;
import org.apache.jena.riot.Lang;

/**
 * Utility class for RDF content type handling.
 * Provides mapping between MIME types and Jena Lang formats.
 */
public final class RdfContentTypeUtil {

  private RdfContentTypeUtil() {
    // Utility class - prevent instantiation
  }

  /**
   * Determines the Apache Jena Lang from a content type string.
   *
   * @param contentType the content type (e.g., "text/turtle", "application/rdf+xml")
   * @return the corresponding Lang, or null if unsupported
   */
  public static Lang determineLang(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return Lang.TURTLE; // Default to Turtle
    }

    // Handle content type with quality values and parameters (e.g., charset)
    String cleanType = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);

    return switch (cleanType) {
      case "text/turtle", "application/x-turtle" -> Lang.TURTLE;
      case "application/n-triples", "text/plain" -> Lang.NTRIPLES;
      case "application/ld+json", "application/json" -> Lang.JSONLD;
      case "application/rdf+xml", "application/xml" -> Lang.RDFXML;
      case "text/n3", "text/rdf+n3" -> Lang.N3;
      case "application/n-quads" -> Lang.NQUADS;
      case "application/trig" -> Lang.TRIG;
      default -> null;
    };
  }

  /**
   * Gets the content type string for a given Lang.
   *
   * @param lang the Apache Jena Lang
   * @return the corresponding content type string
   */
  public static String getContentType(Lang lang) {
    if (lang == null) {
      return "text/turtle";
    }

    if (lang.equals(Lang.TURTLE)) {
      return "text/turtle";
    } else if (lang.equals(Lang.NTRIPLES)) {
      return "application/n-triples";
    } else if (lang.equals(Lang.JSONLD)) {
      return "application/ld+json";
    } else if (lang.equals(Lang.RDFXML)) {
      return "application/rdf+xml";
    } else if (lang.equals(Lang.N3)) {
      return "text/n3";
    } else if (lang.equals(Lang.NQUADS)) {
      return "application/n-quads";
    } else if (lang.equals(Lang.TRIG)) {
      return "application/trig";
    }

    return "text/turtle";
  }
}
