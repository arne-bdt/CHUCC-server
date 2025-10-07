package org.chucc.vcserver.testutil;

/**
 * Centralized test constants for consistent test data across the test suite.
 */
public final class TestConstants {

  private TestConstants() {
    // Utility class - prevent instantiation
  }

  // Dataset names
  public static final String DATASET_DEFAULT = "default";
  public static final String DATASET_TEST = "test-dataset";

  // Branch names
  public static final String BRANCH_MAIN = "main";
  public static final String BRANCH_FEATURE = "feature-branch";
  public static final String BRANCH_DEVELOP = "develop";

  // Commit authors
  public static final String AUTHOR_SYSTEM = "System";
  public static final String AUTHOR_ALICE = "Alice <mailto:alice@example.org>";
  public static final String AUTHOR_BOB = "Bob <mailto:bob@example.org>";

  // Common commit messages
  public static final String MSG_INITIAL = "Initial commit";
  public static final String MSG_ADD_DATA = "Add data";
  public static final String MSG_UPDATE_DATA = "Update data";

  // Test URIs
  public static final String URI_EXAMPLE_BASE = "http://example.org/";
  public static final String URI_SUBJECT_1 = "http://example.org/s1";
  public static final String URI_SUBJECT_2 = "http://example.org/s2";
  public static final String URI_PREDICATE_1 = "http://example.org/p1";
  public static final String URI_PREDICATE_2 = "http://example.org/p2";

  // Common RDF Patches
  public static final String PATCH_SIMPLE = "TX .\n"
      + "A <http://example.org/s> <http://example.org/p> \"value\" .\n"
      + "TC .";

  public static final String PATCH_TWO_TRIPLES = "TX .\n"
      + "A <http://example.org/s1> <http://example.org/p1> \"value1\" .\n"
      + "A <http://example.org/s2> <http://example.org/p2> \"value2\" .\n"
      + "TC .";

  // Common RDF Content (Turtle)
  public static final String TURTLE_SIMPLE = "@prefix ex: <http://example.org/> .\n"
      + "ex:subject ex:predicate \"value\" .";

  public static final String TURTLE_TWO_TRIPLES = "@prefix ex: <http://example.org/> .\n"
      + "ex:s1 ex:p1 \"value1\" .\n"
      + "ex:s2 ex:p2 \"value2\" .";

  // Common RDF Content (N-Triples)
  public static final String NTRIPLES_SIMPLE =
      "<http://example.org/s> <http://example.org/p> \"value\" .";

  // Common RDF Content (JSON-LD)
  public static final String JSONLD_SIMPLE = "{\n"
      + "  \"@context\": {\"ex\": \"http://example.org/\"},\n"
      + "  \"@id\": \"ex:subject\",\n"
      + "  \"ex:predicate\": \"value\"\n"
      + "}";

  // Invalid/Malformed RDF
  public static final String MALFORMED_TURTLE = "@prefix ex: <http://example.org/ .\n"
      + "ex:subject ex:predicate";

  // Tag names
  public static final String TAG_V1 = "v1.0.0";
  public static final String TAG_V2 = "v2.0.0";
}
