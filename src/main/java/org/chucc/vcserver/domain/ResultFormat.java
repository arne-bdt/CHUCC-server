package org.chucc.vcserver.domain;

/**
 * SPARQL query result formats.
 * Defines supported output formats for query results per SPARQL 1.2 Protocol.
 */
public enum ResultFormat {
  /**
   * SPARQL Query Results JSON Format.
   * Media type: application/sparql-results+json
   */
  JSON,

  /**
   * SPARQL Query Results XML Format.
   * Media type: application/sparql-results+xml
   */
  XML,

  /**
   * Comma-Separated Values.
   * Media type: text/csv
   */
  CSV,

  /**
   * Tab-Separated Values.
   * Media type: text/tab-separated-values
   */
  TSV,

  /**
   * Turtle format for CONSTRUCT/DESCRIBE queries.
   * Media type: text/turtle
   */
  TURTLE,

  /**
   * RDF/XML format for CONSTRUCT/DESCRIBE queries.
   * Media type: application/rdf+xml
   */
  RDF_XML
}
