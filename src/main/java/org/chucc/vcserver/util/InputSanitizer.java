package org.chucc.vcserver.util;

import java.util.regex.Pattern;

/**
 * Utility class for sanitizing user inputs to prevent injection attacks and XSS.
 * Provides methods to clean author names, commit messages, and other text inputs.
 */
public final class InputSanitizer {

  /**
   * Maximum length for author names.
   */
  public static final int MAX_AUTHOR_LENGTH = 256;

  /**
   * Maximum length for commit messages.
   */
  public static final int MAX_MESSAGE_LENGTH = 4096;

  /**
   * Pattern to detect HTML/XML tags.
   */
  private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");

  /**
   * Pattern to detect script tags and javascript: protocol.
   */
  private static final Pattern SCRIPT_PATTERN =
      Pattern.compile("(?i)<script[^>]*>.*?</script>|javascript:|on\\w+\\s*=");

  /**
   * Pattern for control characters (except tab, newline, carriage return).
   */
  private static final Pattern CONTROL_CHARS =
      Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

  private InputSanitizer() {
    // Utility class - prevent instantiation
  }

  /**
   * Sanitizes an author name for safe storage and display.
   * Removes HTML tags, scripts, and control characters.
   *
   * @param author the author name to sanitize
   * @return the sanitized author name
   * @throws IllegalArgumentException if author is null, blank, or exceeds max length
   */
  public static String sanitizeAuthor(String author) {
    if (author == null) {
      throw new IllegalArgumentException("Author cannot be null");
    }

    String trimmed = author.trim();

    if (trimmed.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }

    if (trimmed.length() > MAX_AUTHOR_LENGTH) {
      throw new IllegalArgumentException(
          "Author exceeds maximum length of " + MAX_AUTHOR_LENGTH + " characters"
      );
    }

    // Detect and reject script injections
    if (SCRIPT_PATTERN.matcher(trimmed).find()) {
      throw new IllegalArgumentException(
          "Author contains disallowed content (script tags or javascript:)"
      );
    }

    // Remove HTML tags
    String sanitized = HTML_TAG_PATTERN.matcher(trimmed).replaceAll("");

    // Remove control characters
    sanitized = CONTROL_CHARS.matcher(sanitized).replaceAll("");

    // Normalize whitespace
    sanitized = sanitized.replaceAll("\\s+", " ").trim();

    if (sanitized.isBlank()) {
      throw new IllegalArgumentException("Author contains only invalid characters");
    }

    return sanitized;
  }

  /**
   * Sanitizes a commit message for safe storage and display.
   * Removes script tags and control characters but preserves formatting.
   *
   * @param message the commit message to sanitize
   * @return the sanitized commit message
   * @throws IllegalArgumentException if message is null, blank, or exceeds max length
   */
  public static String sanitizeMessage(String message) {
    if (message == null) {
      throw new IllegalArgumentException("Message cannot be null");
    }

    String trimmed = message.trim();

    if (trimmed.isBlank()) {
      throw new IllegalArgumentException("Message cannot be blank");
    }

    if (trimmed.length() > MAX_MESSAGE_LENGTH) {
      throw new IllegalArgumentException(
          "Message exceeds maximum length of " + MAX_MESSAGE_LENGTH + " characters"
      );
    }

    // Detect and reject script injections
    if (SCRIPT_PATTERN.matcher(trimmed).find()) {
      throw new IllegalArgumentException(
          "Message contains disallowed content (script tags or javascript:)"
      );
    }

    // Remove HTML tags (but keep message formatting like newlines)
    String sanitized = HTML_TAG_PATTERN.matcher(trimmed).replaceAll("");

    // Remove control characters (except tab, newline, carriage return)
    sanitized = CONTROL_CHARS.matcher(sanitized).replaceAll("");

    // Normalize excessive whitespace but preserve paragraph breaks
    sanitized = sanitized.replaceAll("[ \\t]+", " ");  // Multiple spaces/tabs to single space
    sanitized = sanitized.replaceAll("\\n{3,}", "\n\n");  // Max 2 consecutive newlines

    sanitized = sanitized.trim();

    if (sanitized.isBlank()) {
      throw new IllegalArgumentException("Message contains only invalid characters");
    }

    return sanitized;
  }

  /**
   * Sanitizes a dataset or branch name.
   * Allows alphanumeric, hyphen, underscore, and period only.
   *
   * @param name the name to sanitize
   * @return the sanitized name
   * @throws IllegalArgumentException if name is null, blank, contains invalid characters,
   *     or exceeds max length
   */
  public static String sanitizeName(String name) {
    if (name == null) {
      throw new IllegalArgumentException("Name cannot be null");
    }

    String trimmed = name.trim();

    if (trimmed.isBlank()) {
      throw new IllegalArgumentException("Name cannot be blank");
    }

    if (trimmed.length() > MAX_AUTHOR_LENGTH) {
      throw new IllegalArgumentException(
          "Name exceeds maximum length of " + MAX_AUTHOR_LENGTH + " characters"
      );
    }

    // Only allow alphanumeric, hyphen, underscore, period
    if (!trimmed.matches("[a-zA-Z0-9._-]+")) {
      throw new IllegalArgumentException(
          "Name contains invalid characters. Only alphanumeric, hyphen, underscore, "
              + "and period are allowed"
      );
    }

    return trimmed;
  }
}
