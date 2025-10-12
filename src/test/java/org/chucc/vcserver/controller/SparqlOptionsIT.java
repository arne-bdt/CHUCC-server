package org.chucc.vcserver.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for SPARQL OPTIONS endpoint with actual configuration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class SparqlOptionsIT {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void optionsEndpoint_advertisesVersionControlSupport() {
    // When
    ResponseEntity<Void> response = restTemplate.exchange(
        "/sparql",
        HttpMethod.OPTIONS,
        HttpEntity.EMPTY,
        Void.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    HttpHeaders headers = response.getHeaders();

    // Check standard headers
    assertThat(headers.getFirst(HttpHeaders.ALLOW)).contains("GET", "POST", "OPTIONS");

    // Check version control headers
    assertThat(headers.getFirst("SPARQL-Version-Control")).isEqualTo("1.0");
    assertThat(headers.getFirst("SPARQL-VC-Level")).isEqualTo("2");

    // Check RDF Patch support
    assertThat(headers.getFirst(HttpHeaders.ACCEPT_PATCH)).isEqualTo("text/rdf-patch");

    // Check enabled features
    String features = headers.getFirst("SPARQL-VC-Features");
    assertThat(features).contains("commits", "branches", "history", "rdf-patch");
    assertThat(features).contains("merge", "conflict-detection", "tags");
    assertThat(features).contains("revert", "reset", "cherry-pick", "blame");

    // Check Link header
    assertThat(headers.getFirst(HttpHeaders.LINK)).isEqualTo("</version>; rel=\"version-control\"");
  }
}
