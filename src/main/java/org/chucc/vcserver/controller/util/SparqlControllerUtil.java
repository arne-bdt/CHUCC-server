package org.chucc.vcserver.controller.util;

import org.chucc.vcserver.domain.ResultFormat;
import org.chucc.vcserver.dto.ProblemDetail;
import org.springframework.http.MediaType;

/**
 * Utility class for SPARQL controller operations.
 * Provides common helper methods for result formatting, error handling, and serialization.
 */
public final class SparqlControllerUtil {

  private SparqlControllerUtil() {
    // Utility class - prevent instantiation
  }

  /**
   * Determines the result format from the Accept header.
   *
   * @param acceptHeader the Accept header value
   * @return the determined result format (defaults to JSON per SPARQL 1.1 Protocol §2.1)
   */
  public static ResultFormat determineResultFormat(String acceptHeader) {
    if (acceptHeader == null || acceptHeader.isEmpty()) {
      return ResultFormat.JSON;
    }

    String lowerAccept = acceptHeader.toLowerCase(java.util.Locale.ROOT);

    if (lowerAccept.contains("application/sparql-results+json")) {
      return ResultFormat.JSON;
    } else if (lowerAccept.contains("application/sparql-results+xml")) {
      return ResultFormat.XML;
    } else if (lowerAccept.contains("text/csv")) {
      return ResultFormat.CSV;
    } else if (lowerAccept.contains("text/tab-separated-values")
        || lowerAccept.contains("text/tsv")) {
      return ResultFormat.TSV;
    } else if (lowerAccept.contains("text/turtle")) {
      return ResultFormat.TURTLE;
    } else if (lowerAccept.contains("application/rdf+xml")) {
      return ResultFormat.RDF_XML;
    }

    return ResultFormat.JSON;
  }

  /**
   * Maps result format to HTTP media type.
   *
   * @param format the result format
   * @return the corresponding media type
   */
  public static MediaType getMediaType(ResultFormat format) {
    switch (format) {
      case JSON:
        return MediaType.parseMediaType("application/sparql-results+json");
      case XML:
        return MediaType.parseMediaType("application/sparql-results+xml");
      case CSV:
        return MediaType.parseMediaType("text/csv");
      case TSV:
        return MediaType.parseMediaType("text/tab-separated-values");
      case TURTLE:
        return MediaType.parseMediaType("text/turtle");
      case RDF_XML:
        return MediaType.parseMediaType("application/rdf+xml");
      default:
        return MediaType.parseMediaType("application/sparql-results+json");
    }
  }

  /**
   * Serializes a ProblemDetail to JSON string.
   *
   * @param problem the problem detail to serialize
   * @return JSON string representation
   */
  public static String serializeProblemDetail(ProblemDetail problem) {
    StringBuilder json = new StringBuilder();
    json.append("{");
    json.append("\"type\":\"").append(escapeJson(problem.getType())).append("\",");
    json.append("\"title\":\"").append(escapeJson(problem.getTitle())).append("\",");
    json.append("\"status\":").append(problem.getStatus());
    if (problem.getCode() != null) {
      json.append(",\"code\":\"").append(escapeJson(problem.getCode())).append("\"");
    }
    if (problem.getDetail() != null) {
      json.append(",\"detail\":\"").append(escapeJson(problem.getDetail())).append("\"");
    }
    json.append("}");
    return json.toString();
  }

  /**
   * Escapes special characters in JSON strings.
   *
   * @param str the string to escape
   * @return the escaped string
   */
  public static String escapeJson(String str) {
    if (str == null) {
      return "";
    }
    return str.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
