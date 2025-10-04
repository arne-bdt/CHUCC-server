package org.chucc.vcserver.event;

/**
 * Constants for Kafka event headers.
 * Headers provide metadata about events for routing, filtering, and processing.
 */
public final class EventHeaders {
  /**
   * Header key for the commit ID.
   */
  public static final String COMMIT_ID = "commitId";

  /**
   * Header key for the branch name.
   */
  public static final String BRANCH = "branch";

  /**
   * Header key for the content type (always text/rdf-patch; charset=utf-8 for patches).
   */
  public static final String CONTENT_TYPE = "contentType";

  /**
   * Content type value for RDF Patch content.
   */
  public static final String RDF_PATCH_CONTENT_TYPE = "text/rdf-patch; charset=utf-8";

  /**
   * Header key for the dataset name.
   */
  public static final String DATASET = "dataset";

  /**
   * Header key for the event type.
   */
  public static final String EVENT_TYPE = "eventType";

  private EventHeaders() {
    throw new UnsupportedOperationException("Utility class");
  }
}
