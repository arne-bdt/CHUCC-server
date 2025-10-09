package org.chucc.vcserver.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InputSanitizerTest {

  @Test
  void sanitizeAuthor_shouldAcceptValidAuthor() {
    String result = InputSanitizer.sanitizeAuthor("John Doe");
    assertThat(result).isEqualTo("John Doe");
  }

  @Test
  void sanitizeAuthor_shouldTrimWhitespace() {
    String result = InputSanitizer.sanitizeAuthor("  Alice  ");
    assertThat(result).isEqualTo("Alice");
  }

  @Test
  void sanitizeAuthor_shouldRemoveHtmlTags() {
    String result = InputSanitizer.sanitizeAuthor("John <b>Doe</b>");
    assertThat(result).isEqualTo("John Doe");
  }

  @Test
  void sanitizeAuthor_shouldRejectScriptTags() {
    assertThatThrownBy(() -> InputSanitizer.sanitizeAuthor("John <script>alert(1)</script>"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("script tags");
  }

  @Test
  void sanitizeAuthor_shouldRejectJavascriptProtocol() {
    assertThatThrownBy(() -> InputSanitizer.sanitizeAuthor("javascript:alert(1)"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("javascript:");
  }

  @Test
  void sanitizeAuthor_shouldRejectOnEventHandlers() {
    assertThatThrownBy(() -> InputSanitizer.sanitizeAuthor("John onclick=alert(1)"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("script tags or javascript:");
  }

  @Test
  void sanitizeAuthor_shouldRemoveControlCharacters() {
    String result = InputSanitizer.sanitizeAuthor("John\u0000Doe");
    assertThat(result).isEqualTo("JohnDoe");
  }

  @Test
  void sanitizeAuthor_shouldRejectNull() {
    assertThatThrownBy(() -> InputSanitizer.sanitizeAuthor(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be null");
  }

  @Test
  void sanitizeAuthor_shouldRejectBlank() {
    assertThatThrownBy(() -> InputSanitizer.sanitizeAuthor("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be blank");
  }

  @Test
  void sanitizeAuthor_shouldRejectTooLong() {
    String tooLong = "a".repeat(InputSanitizer.MAX_AUTHOR_LENGTH + 1);
    assertThatThrownBy(() -> InputSanitizer.sanitizeAuthor(tooLong))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeds maximum length");
  }

  @Test
  void sanitizeMessage_shouldAcceptValidMessage() {
    String result = InputSanitizer.sanitizeMessage("Fix bug in parser");
    assertThat(result).isEqualTo("Fix bug in parser");
  }

  @Test
  void sanitizeMessage_shouldPreserveNewlines() {
    String result = InputSanitizer.sanitizeMessage("Line 1\nLine 2\nLine 3");
    assertThat(result).isEqualTo("Line 1\nLine 2\nLine 3");
  }

  @Test
  void sanitizeMessage_shouldLimitConsecutiveNewlines() {
    String result = InputSanitizer.sanitizeMessage("Line 1\n\n\n\nLine 2");
    assertThat(result).isEqualTo("Line 1\n\nLine 2");
  }

  @Test
  void sanitizeMessage_shouldRemoveHtmlTags() {
    String result = InputSanitizer.sanitizeMessage("Fix <b>critical</b> bug");
    assertThat(result).isEqualTo("Fix critical bug");
  }

  @Test
  void sanitizeMessage_shouldRejectScriptTags() {
    assertThatThrownBy(() ->
        InputSanitizer.sanitizeMessage("Message <script>alert(1)</script>"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("script tags");
  }

  @Test
  void sanitizeMessage_shouldRejectNull() {
    assertThatThrownBy(() -> InputSanitizer.sanitizeMessage(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be null");
  }

  @Test
  void sanitizeMessage_shouldRejectBlank() {
    assertThatThrownBy(() -> InputSanitizer.sanitizeMessage("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be blank");
  }

  @Test
  void sanitizeMessage_shouldRejectTooLong() {
    String tooLong = "a".repeat(InputSanitizer.MAX_MESSAGE_LENGTH + 1);
    assertThatThrownBy(() -> InputSanitizer.sanitizeMessage(tooLong))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeds maximum length");
  }

  @Test
  void sanitizeName_shouldAcceptValidName() {
    String result = InputSanitizer.sanitizeName("my-dataset_123");
    assertThat(result).isEqualTo("my-dataset_123");
  }

  @Test
  void sanitizeName_shouldAcceptPeriods() {
    String result = InputSanitizer.sanitizeName("my.dataset");
    assertThat(result).isEqualTo("my.dataset");
  }

  @Test
  void sanitizeName_shouldRejectSpaces() {
    assertThatThrownBy(() -> InputSanitizer.sanitizeName("my dataset"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid characters");
  }

  @Test
  void sanitizeName_shouldRejectSpecialCharacters() {
    assertThatThrownBy(() -> InputSanitizer.sanitizeName("dataset@123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid characters");
  }

  @Test
  void sanitizeName_shouldRejectNull() {
    assertThatThrownBy(() -> InputSanitizer.sanitizeName(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be null");
  }

  @Test
  void sanitizeName_shouldRejectBlank() {
    assertThatThrownBy(() -> InputSanitizer.sanitizeName("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be blank");
  }

  @Test
  void sanitizeName_shouldRejectTooLong() {
    String tooLong = "a".repeat(InputSanitizer.MAX_AUTHOR_LENGTH + 1);
    assertThatThrownBy(() -> InputSanitizer.sanitizeName(tooLong))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeds maximum length");
  }
}
