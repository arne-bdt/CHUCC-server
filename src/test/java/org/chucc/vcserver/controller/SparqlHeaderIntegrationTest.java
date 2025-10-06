package org.chucc.vcserver.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class SparqlHeaderIntegrationTest {

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
        // Then: Request is accepted (501 stub implementation)
        .andExpect(status().isNotImplemented());
  }

  @Test
  void getQuery_withVcCommitHeader_accepted() throws Exception {
    // Given: A SPARQL query with SPARQL-VC-Commit header
    String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";

    // When: GET with SPARQL-VC-Commit header
    mockMvc.perform(get("/sparql")
            .param("query", query)
            .header("SPARQL-VC-Commit", "01936f7e-1234-7890-abcd-ef0123456789"))
        // Then: Request is accepted (501 stub implementation)
        .andExpect(status().isNotImplemented());
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
        // Then: Request is accepted (headers are optional)
        .andExpect(status().isNotImplemented());
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
        // Then: Request is accepted (headers are optional)
        .andExpect(status().isNotImplemented());
  }

  @Test
  void getQuery_withBranchParameter_accepted() throws Exception {
    // Given: A SPARQL query with branch parameter (not header)
    String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";

    // When: GET with branch query parameter
    mockMvc.perform(get("/sparql")
            .param("query", query)
            .param("branch", "feature-x"))
        // Then: Request is accepted
        .andExpect(status().isNotImplemented());
  }

  @Test
  void getQuery_withCommitParameter_accepted() throws Exception {
    // Given: A SPARQL query with commit parameter
    String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";

    // When: GET with commit query parameter
    mockMvc.perform(get("/sparql")
            .param("query", query)
            .param("commit", "01936f7e-1234-7890-abcd-ef0123456789"))
        // Then: Request is accepted
        .andExpect(status().isNotImplemented());
  }

  @Test
  void getQuery_withAsOfParameter_accepted() throws Exception {
    // Given: A SPARQL query with asOf parameter
    String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";

    // When: GET with asOf query parameter
    mockMvc.perform(get("/sparql")
            .param("query", query)
            .param("asOf", "2025-10-03T12:00:00Z"))
        // Then: Request is accepted
        .andExpect(status().isNotImplemented());
  }
}
