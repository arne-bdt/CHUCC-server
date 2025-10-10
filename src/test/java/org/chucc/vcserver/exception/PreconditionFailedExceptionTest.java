package org.chucc.vcserver.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PreconditionFailedException}.
 */
class PreconditionFailedExceptionTest {

  @Test
  void constructor_shouldSetExpectedAndActual() {
    String expected = "01936c7f-8a2e-7890-abcd-ef1234567890";
    String actual = "01936c81-2222-7890-abcd-ef1234567890";

    PreconditionFailedException exception = new PreconditionFailedException(expected, actual);

    assertThat(exception.getExpected()).isEqualTo(expected);
    assertThat(exception.getActual()).isEqualTo(actual);
  }

  @Test
  void constructor_shouldSetErrorCode() {
    PreconditionFailedException exception = new PreconditionFailedException(
        "expected-id", "actual-id");
    assertThat(exception.getCode()).isEqualTo("concurrent_modification_conflict");
  }

  @Test
  void constructor_shouldSetHttpStatus409() {
    PreconditionFailedException exception = new PreconditionFailedException(
        "expected-id", "actual-id");
    assertThat(exception.getStatus()).isEqualTo(409);
  }

  @Test
  void constructor_shouldCreateDescriptiveMessage() {
    String expected = "01936c7f-8a2e-7890-abcd-ef1234567890";
    String actual = "01936c81-2222-7890-abcd-ef1234567890";

    PreconditionFailedException exception = new PreconditionFailedException(expected, actual);

    assertThat(exception.getMessage())
        .contains("If-Match precondition failed")
        .contains(expected)
        .contains(actual);
  }
}
