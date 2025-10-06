package org.chucc.vcserver.util;

/**
 * Utility class for working with HTTP ETags in the context of version control.
 * <p>
 * ETags are used for optimistic concurrency control. For branch resources,
 * the ETag represents the current HEAD commit ID. For commit resources,
 * the ETag is the commit ID itself (since commits are immutable).
 * </p>
 */
public final class EtagUtil {

  private EtagUtil() {
    // Utility class - prevent instantiation
  }

  /**
   * Creates a strong ETag from a commit ID.
   * <p>
   * Strong ETags are enclosed in double quotes and indicate that the resource
   * representation is byte-for-byte identical.
   * </p>
   *
   * @param commitId the commit ID to convert to an ETag
   * @return the strong ETag value (e.g., "01936c7f-8a2e-7890-abcd-ef1234567890")
   */
  public static String createStrongEtag(String commitId) {
    return "\"" + commitId + "\"";
  }

  /**
   * Parses a commit ID from an ETag value.
   * <p>
   * Removes surrounding quotes if present. Returns null if the input is null.
   * </p>
   *
   * @param etag the ETag value to parse
   * @return the commit ID without quotes, or null if input is null
   */
  public static String parseEtag(String etag) {
    if (etag == null) {
      return null;
    }
    // Remove quotes if present
    return etag.replaceAll("^\"|\"$", "");
  }

  /**
   * Checks if an ETag matches an expected commit ID.
   * <p>
   * Parses the ETag and compares it to the expected commit ID.
   * Returns false if the ETag is null or doesn't match.
   * </p>
   *
   * @param etag the ETag value to check
   * @param expectedCommitId the expected commit ID
   * @return true if the ETag matches the expected commit ID, false otherwise
   */
  public static boolean matches(String etag, String expectedCommitId) {
    String parsed = parseEtag(etag);
    return parsed != null && parsed.equals(expectedCommitId);
  }
}
