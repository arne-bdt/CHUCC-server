package org.chucc.vcserver.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for PrefixValidator.
 */
class PrefixValidatorTest {

  // ==============================================
  // Prefix Name Validation Tests
  // ==============================================

  @Test
  void validatePrefixName_shouldAcceptSingleLetter() {
    assertThatCode(() -> PrefixValidator.validatePrefixName("a"))
        .doesNotThrowAnyException();
    assertThatCode(() -> PrefixValidator.validatePrefixName("Z"))
        .doesNotThrowAnyException();
  }

  @Test
  void validatePrefixName_shouldAcceptValidNames() {
    assertThatCode(() -> PrefixValidator.validatePrefixName("rdf"))
        .doesNotThrowAnyException();
    assertThatCode(() -> PrefixValidator.validatePrefixName("foaf"))
        .doesNotThrowAnyException();
    assertThatCode(() -> PrefixValidator.validatePrefixName("schema"))
        .doesNotThrowAnyException();
    assertThatCode(() -> PrefixValidator.validatePrefixName("ex"))
        .doesNotThrowAnyException();
    assertThatCode(() -> PrefixValidator.validatePrefixName("myPrefix123"))
        .doesNotThrowAnyException();
    assertThatCode(() -> PrefixValidator.validatePrefixName("my_prefix"))
        .doesNotThrowAnyException();
    assertThatCode(() -> PrefixValidator.validatePrefixName("my-prefix"))
        .doesNotThrowAnyException();
  }

  @Test
  void validatePrefixName_shouldAcceptNamesWithDotsInMiddle() {
    assertThatCode(() -> PrefixValidator.validatePrefixName("dcterms.title"))
        .doesNotThrowAnyException();
    assertThatCode(() -> PrefixValidator.validatePrefixName("org.example"))
        .doesNotThrowAnyException();
  }

  @Test
  void validatePrefixName_shouldRejectStartingWithNumber() {
    assertThatThrownBy(() -> PrefixValidator.validatePrefixName("1invalid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid prefix name");
  }

  @Test
  void validatePrefixName_shouldRejectStartingWithUnderscore() {
    assertThatThrownBy(() -> PrefixValidator.validatePrefixName("_invalid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid prefix name");
  }

  @Test
  void validatePrefixName_shouldRejectStartingWithHyphen() {
    assertThatThrownBy(() -> PrefixValidator.validatePrefixName("-invalid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid prefix name");
  }

  @Test
  void validatePrefixName_shouldRejectStartingWithDot() {
    assertThatThrownBy(() -> PrefixValidator.validatePrefixName(".invalid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid prefix name");
  }

  @Test
  void validatePrefixName_shouldRejectEndingWithDot() {
    assertThatThrownBy(() -> PrefixValidator.validatePrefixName("invalid."))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid prefix name");
  }

  @Test
  void validatePrefixName_shouldRejectConsecutiveDots() {
    assertThatThrownBy(() -> PrefixValidator.validatePrefixName("in..valid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid prefix name");
  }

  @Test
  void validatePrefixName_shouldRejectSpecialCharacters() {
    assertThatThrownBy(() -> PrefixValidator.validatePrefixName("in@valid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid prefix name");
    assertThatThrownBy(() -> PrefixValidator.validatePrefixName("in#valid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid prefix name");
    assertThatThrownBy(() -> PrefixValidator.validatePrefixName("in$valid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid prefix name");
  }

  @Test
  void validatePrefixName_shouldRejectEmpty() {
    assertThatThrownBy(() -> PrefixValidator.validatePrefixName(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be empty");
  }

  @Test
  void validatePrefixName_shouldRejectNull() {
    assertThatThrownBy(() -> PrefixValidator.validatePrefixName(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("cannot be null");
  }

  // ==============================================
  // IRI Validation Tests
  // ==============================================

  @Test
  void validateAbsoluteIri_shouldAcceptHttpIris() {
    assertThatCode(() -> PrefixValidator.validateAbsoluteIri("http://example.org/"))
        .doesNotThrowAnyException();
    assertThatCode(() -> PrefixValidator.validateAbsoluteIri("https://example.org/"))
        .doesNotThrowAnyException();
    assertThatCode(() -> PrefixValidator.validateAbsoluteIri("http://example.org/vocab#"))
        .doesNotThrowAnyException();
  }

  @Test
  void validateAbsoluteIri_shouldAcceptOtherSchemes() {
    assertThatCode(() -> PrefixValidator.validateAbsoluteIri("urn:isbn:0451450523"))
        .doesNotThrowAnyException();
    assertThatCode(() -> PrefixValidator.validateAbsoluteIri("ftp://ftp.example.org/"))
        .doesNotThrowAnyException();
    assertThatCode(() -> PrefixValidator.validateAbsoluteIri("mailto:user@example.org"))
        .doesNotThrowAnyException();
  }

  @Test
  void validateAbsoluteIri_shouldRejectRelativeIris() {
    assertThatThrownBy(() -> PrefixValidator.validateAbsoluteIri("../relative"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be absolute");
    assertThatThrownBy(() -> PrefixValidator.validateAbsoluteIri("/absolute/path"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be absolute");
    assertThatThrownBy(() -> PrefixValidator.validateAbsoluteIri("relative/path"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be absolute");
  }

  @Test
  void validateAbsoluteIri_shouldRejectEmpty() {
    assertThatThrownBy(() -> PrefixValidator.validateAbsoluteIri(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be empty");
  }

  @Test
  void validateAbsoluteIri_shouldRejectNull() {
    assertThatThrownBy(() -> PrefixValidator.validateAbsoluteIri(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("cannot be null");
  }

  @Test
  void validateAbsoluteIri_shouldRejectInvalidSyntax() {
    assertThatThrownBy(() -> PrefixValidator.validateAbsoluteIri("http://invalid iri"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid IRI syntax");
  }
}
