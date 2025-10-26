package org.chucc.vcserver.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.chucc.vcserver.exception.SelectorConflictException;
import org.chucc.vcserver.testutil.ExpectedErrorContext;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for GraphParameterValidator.
 */
class GraphParameterValidatorTest {

  @Test
  void validateGraphParameter_shouldAcceptValidGraphIri() {
    String graph = "http://example.org/graph1";

    assertThatCode(() -> GraphParameterValidator.validateGraphParameter(graph, null))
        .doesNotThrowAnyException();

    assertThatCode(() -> GraphParameterValidator.validateGraphParameter(graph, false))
        .doesNotThrowAnyException();
  }

  @Test
  void validateGraphParameter_shouldAcceptDefaultTrue() {
    assertThatCode(() -> GraphParameterValidator.validateGraphParameter(null, true))
        .doesNotThrowAnyException();
  }

  @Test
  void validateGraphParameter_shouldRejectBothGraphAndDefault() {
    String graph = "http://example.org/graph1";

    assertThatThrownBy(() -> GraphParameterValidator.validateGraphParameter(graph, true))
        .isInstanceOf(SelectorConflictException.class)
        .hasMessageContaining("graph")
        .hasMessageContaining("default");
  }

  @Test
  void validateGraphParameter_shouldRejectNeitherGraphNorDefault() {
    assertThatThrownBy(() -> GraphParameterValidator.validateGraphParameter(null, null))
        .isInstanceOf(SelectorConflictException.class)
        .hasMessageContaining("graph")
        .hasMessageContaining("default");

    assertThatThrownBy(() -> GraphParameterValidator.validateGraphParameter(null, false))
        .isInstanceOf(SelectorConflictException.class)
        .hasMessageContaining("graph")
        .hasMessageContaining("default");
  }

  @Test
  void validateGraphParameter_shouldRejectBlankGraphIri() {
    assertThatThrownBy(() -> GraphParameterValidator.validateGraphParameter("", null))
        .isInstanceOf(SelectorConflictException.class);

    assertThatThrownBy(() -> GraphParameterValidator.validateGraphParameter("   ", null))
        .isInstanceOf(SelectorConflictException.class);
  }

  @Test
  @SuppressWarnings("try")  // Suppress "resource never referenced" - used for MDC side-effects
  void validateGraphParameter_shouldRejectInvalidGraphIri() {
    try (var ignored = ExpectedErrorContext.suppress("Bad character in IRI")) {
      assertThatThrownBy(() -> GraphParameterValidator.validateGraphParameter("not a valid iri", null))
          .isInstanceOf(SelectorConflictException.class)
          .hasMessageContaining("Invalid IRI");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @SuppressWarnings("try")  // Suppress "resource never referenced" - used for MDC side-effects
  void validateGraphParameter_shouldRejectIriWithSpaces() {
    try (var ignored = ExpectedErrorContext.suppress("Bad character in IRI")) {
      assertThatThrownBy(() ->
              GraphParameterValidator.validateGraphParameter("http://example.org/has spaces", null))
          .isInstanceOf(SelectorConflictException.class)
          .hasMessageContaining("Invalid IRI");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void validateGraphIri_shouldAcceptValidIri() {
    assertThatCode(() -> GraphParameterValidator.validateGraphIri("http://example.org/graph1"))
        .doesNotThrowAnyException();
  }

  @Test
  void validateGraphIri_shouldAcceptUrn() {
    assertThatCode(() -> GraphParameterValidator.validateGraphIri("urn:example:graph1"))
        .doesNotThrowAnyException();
  }

  @Test
  void validateGraphIri_shouldAcceptHttpsIri() {
    assertThatCode(() -> GraphParameterValidator.validateGraphIri("https://example.org/secure"))
        .doesNotThrowAnyException();
  }

  @Test
  void validateGraphIri_shouldAcceptIriWithFragment() {
    assertThatCode(() -> GraphParameterValidator.validateGraphIri("http://example.org/graph#main"))
        .doesNotThrowAnyException();
  }

  @Test
  void validateGraphIri_shouldAcceptIriWithQuery() {
    assertThatCode(() ->
            GraphParameterValidator.validateGraphIri("http://example.org/graph?version=1"))
        .doesNotThrowAnyException();
  }

  @Test
  void validateGraphIri_shouldAcceptUnicodeIri() {
    assertThatCode(() -> GraphParameterValidator.validateGraphIri("http://example.org/graph-\u00E9"))
        .doesNotThrowAnyException();
  }

  @Test
  void validateGraphIri_shouldRejectNullIri() {
    assertThatThrownBy(() -> GraphParameterValidator.validateGraphIri(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("IRI cannot be null");
  }

  @Test
  void validateGraphIri_shouldRejectBlankIri() {
    assertThatThrownBy(() -> GraphParameterValidator.validateGraphIri(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("IRI cannot be blank");

    assertThatThrownBy(() -> GraphParameterValidator.validateGraphIri("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("IRI cannot be blank");
  }

  @Test
  @SuppressWarnings("try")  // Suppress "resource never referenced" - used for MDC side-effects
  void validateGraphIri_shouldRejectInvalidIri() {
    try (var ignored = ExpectedErrorContext.suppress("Bad character in IRI")) {
      assertThatThrownBy(() -> GraphParameterValidator.validateGraphIri("not a valid iri"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid IRI");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @SuppressWarnings("try")  // Suppress "resource never referenced" - used for MDC side-effects
  void validateGraphIri_shouldRejectIriWithSpaces() {
    try (var ignored = ExpectedErrorContext.suppress("Bad character in IRI")) {
      assertThatThrownBy(() ->
              GraphParameterValidator.validateGraphIri("http://example.org/has spaces"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid IRI");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void validateGraphIri_shouldRejectTooLongIri() {
    // Create an IRI that exceeds MAX_IRI_LENGTH (2048)
    String baseIri = "http://example.org/";
    String tooLongIri = baseIri + "a".repeat(2050);

    assertThatThrownBy(() -> GraphParameterValidator.validateGraphIri(tooLongIri))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeds maximum length");
  }

  @Test
  void validateGraphIri_shouldAcceptMaxLengthIri() {
    // Create an IRI at exactly MAX_IRI_LENGTH (2048)
    String baseIri = "http://example.org/";
    String maxLengthIri = baseIri + "a".repeat(2048 - baseIri.length());

    assertThatCode(() -> GraphParameterValidator.validateGraphIri(maxLengthIri))
        .doesNotThrowAnyException();
  }
}
