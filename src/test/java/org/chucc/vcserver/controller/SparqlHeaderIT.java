package org.chucc.vcserver.controller;

import java.time.Duration;
import java.time.Instant;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SPARQL Controller header handling.
 * Tests the new header names per SPARQL 1.2 Protocol §5.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
class SparqlHeaderIT extends ITFixture {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void postUpdate_withMessageAndAuthorHeaders_accepted() throws Exception {
    // Given: A SPARQL update with new header names
    String sparqlUpdate = "INSERT DATA { "
        + "<http://example.org/alice> <http://xmlns.com/foaf/0.1/name> \"Alice\" "
        + "}";

    // When: POST with SPARQL-VC-Message and SPARQL-VC-Author headers
    mockMvc.perform(post("/sparql")
            .contentType("application/sparql-update")
            .header("SPARQL-VC-Message", "Add Alice")
            .header("SPARQL-VC-Author", "alice@example.org")
            .content(sparqlUpdate))
        // Then: Request is accepted and update succeeds
        .andExpect(status().isAccepted());
  }

  @Test
  void postUpdate_withOnlyMessageHeader_accepted() throws Exception {
    // Given: A SPARQL update with only message header
    String sparqlUpdate = "INSERT DATA { "
        + "<http://example.org/bob> <http://xmlns.com/foaf/0.1/name> \"Bob\" "
        + "}";

    // When: POST with only SPARQL-VC-Message header
    mockMvc.perform(post("/sparql")
            .contentType("application/sparql-update")
            .header("SPARQL-VC-Message", "Add Bob")
            .content(sparqlUpdate))
        // Then: Returns 400 Bad Request (SPARQL-VC-Author required)
        .andExpect(status().isBadRequest());
  }

  @Test
  void postUpdate_withOnlyAuthorHeader_accepted() throws Exception {
    // Given: A SPARQL update with only author header
    String sparqlUpdate = "INSERT DATA { "
        + "<http://example.org/charlie> <http://xmlns.com/foaf/0.1/name> \"Charlie\" "
        + "}";

    // When: POST with only SPARQL-VC-Author header
    mockMvc.perform(post("/sparql")
            .contentType("application/sparql-update")
            .header("SPARQL-VC-Author", "charlie@example.org")
            .content(sparqlUpdate))
        // Then: Returns 400 Bad Request (SPARQL-VC-Message required)
        .andExpect(status().isBadRequest());
  }

  @Test
  void getQuery_withBranchParameter_accepted() throws Exception {
    // Given: A SPARQL query with branch parameter (not header)
    String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";

    // When: GET with branch query parameter (using existing main branch)
    mockMvc.perform(get("/sparql")
            .param("query", query)
            .param("branch", "main"))
        // Then: Query succeeds (endpoint implemented)
        .andExpect(status().isOk());
  }

  @Test
  void getQuery_withCommitParameter_accepted() throws Exception {
    // Given: A SPARQL query with commit parameter
    String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";

    // When: GET with commit query parameter (using existing initial commit)
    mockMvc.perform(get("/sparql")
            .param("query", query)
            .param("commit", initialCommitId.value()))
        // Then: Query succeeds (endpoint implemented)
        .andExpect(status().isOk());
  }

  @Test
  void getQuery_withAsOfParameter_accepted() throws Exception {
    // Given: A SPARQL query with asOf parameter (future timestamp to be after initial commit)
    String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";
    String futureTimestamp = Instant.now().plus(Duration.ofDays(1)).toString();

    // When: GET with asOf query parameter and branch
    mockMvc.perform(get("/sparql")
            .param("query", query)
            .param("branch", "main")
            .param("asOf", futureTimestamp))
        // Then: Query succeeds (endpoint implemented)
        .andExpect(status().isOk());
  }
}
