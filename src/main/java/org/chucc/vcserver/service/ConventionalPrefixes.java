package org.chucc.vcserver.service;

import java.util.Map;

/**
 * Database of conventional prefixes for common RDF namespaces.
 *
 * <p>This is a subset of the prefix.cc database, containing the most widely-used
 * ontologies and vocabularies in the Semantic Web community.
 *
 * @see <a href="https://prefix.cc">prefix.cc</a>
 */
public final class ConventionalPrefixes {

  private ConventionalPrefixes() {
    // Utility class - prevent instantiation
  }

  /**
   * Map of namespace IRI to conventional prefix name.
   */
  private static final Map<String, String> CONVENTIONAL_PREFIXES = Map.ofEntries(
      // Core RDF/OWL/RDFS
      Map.entry("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf"),
      Map.entry("http://www.w3.org/2000/01/rdf-schema#", "rdfs"),
      Map.entry("http://www.w3.org/2002/07/owl#", "owl"),
      Map.entry("http://www.w3.org/2001/XMLSchema#", "xsd"),

      // Popular ontologies
      Map.entry("http://xmlns.com/foaf/0.1/", "foaf"),
      Map.entry("http://purl.org/dc/terms/", "dct"),
      Map.entry("http://purl.org/dc/elements/1.1/", "dc"),
      Map.entry("http://schema.org/", "schema"),
      Map.entry("http://www.w3.org/2004/02/skos/core#", "skos"),

      // Geospatial
      Map.entry("http://www.opengis.net/ont/geosparql#", "geo"),
      Map.entry("http://www.opengis.net/ont/sf#", "sf"),
      Map.entry("http://www.w3.org/2003/01/geo/wgs84_pos#", "wgs84"),

      // DBpedia
      Map.entry("http://dbpedia.org/ontology/", "dbo"),
      Map.entry("http://dbpedia.org/resource/", "dbr"),
      Map.entry("http://dbpedia.org/property/", "dbp"),

      // Provenance
      Map.entry("http://www.w3.org/ns/prov#", "prov"),

      // Time
      Map.entry("http://www.w3.org/2006/time#", "time"),

      // Additional common vocabularies
      Map.entry("http://www.w3.org/ns/org#", "org"),
      Map.entry("http://purl.org/vocab/vann/", "vann"),
      Map.entry("http://www.w3.org/ns/dcat#", "dcat"),
      Map.entry("http://www.w3.org/ns/shacl#", "sh"),
      Map.entry("http://www.w3.org/2008/05/skos-xl#", "skosxl")
  );

  /**
   * Gets the conventional prefix for a namespace IRI.
   *
   * @param namespace the namespace IRI
   * @return the conventional prefix, or null if no conventional prefix exists
   */
  public static String getConventionalPrefix(String namespace) {
    if (namespace == null) {
      return null;
    }
    return CONVENTIONAL_PREFIXES.get(namespace);
  }

  /**
   * Checks if a namespace has a conventional prefix defined.
   *
   * @param namespace the namespace IRI
   * @return true if a conventional prefix exists, false otherwise
   */
  public static boolean hasConventionalPrefix(String namespace) {
    if (namespace == null) {
      return false;
    }
    return CONVENTIONAL_PREFIXES.containsKey(namespace);
  }
}
