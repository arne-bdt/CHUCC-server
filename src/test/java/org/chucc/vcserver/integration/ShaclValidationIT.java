package org.chucc.vcserver.integration;

import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SHACL Validation Protocol.
 *
 * <p>This test class will be expanded in Task 2 with actual validation tests.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class ShaclValidationIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void endpoint_shouldExist() {
    // Arrange
    String url = "/" + getDatasetName() + "/shacl";

    // Act
    ResponseEntity<String> response = restTemplate.postForEntity(
        url,
        "{}",
        String.class
    );

    // Assert
    assertThat(response.getStatusCode())
        .as("Endpoint should exist (not 404)")
        .isNotEqualTo(HttpStatus.NOT_FOUND);
  }

  // Additional tests will be added in Task 2 (Basic Inline Validation)
}
