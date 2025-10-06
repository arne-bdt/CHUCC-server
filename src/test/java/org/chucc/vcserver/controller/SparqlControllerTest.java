package org.chucc.vcserver.controller;

import org.chucc.vcserver.config.VersionControlProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for SPARQL Controller OPTIONS endpoint and conformance toggles.
 */
@WebMvcTest(SparqlController.class)
class SparqlControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private VersionControlProperties vcProperties;

  @Test
  void optionsEndpoint_level2_allFeaturesEnabled() throws Exception {
    // Given: Level 2 with all features enabled
    when(vcProperties.getLevel()).thenReturn(2);
    when(vcProperties.isCommitsEnabled()).thenReturn(true);
    when(vcProperties.isBranchesEnabled()).thenReturn(true);
    when(vcProperties.isHistoryEnabled()).thenReturn(true);
    when(vcProperties.isRdfPatchEnabled()).thenReturn(true);
    when(vcProperties.isMergeEnabled()).thenReturn(true);
    when(vcProperties.isConflictDetectionEnabled()).thenReturn(true);
    when(vcProperties.isTagsEnabled()).thenReturn(true);
    when(vcProperties.isRevertEnabled()).thenReturn(true);
    when(vcProperties.isResetEnabled()).thenReturn(true);
    when(vcProperties.isCherryPickEnabled()).thenReturn(true);
    when(vcProperties.isBlameEnabled()).thenReturn(true);

    // When & Then
    mockMvc.perform(options("/sparql"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ALLOW, "GET, POST, OPTIONS"))
        .andExpect(header().string("SPARQL-Version-Control", "1.0"))
        .andExpect(header().string("SPARQL-VC-Level", "2"))
        .andExpect(header().string(HttpHeaders.ACCEPT_PATCH, "text/rdf-patch"))
        .andExpect(header().string("SPARQL-VC-Features",
            "commits, branches, history, rdf-patch, merge, conflict-detection, tags, revert, reset, cherry-pick, blame"))
        .andExpect(header().string(HttpHeaders.LINK, "</version>; rel=\"version-control\""));
  }

  @Test
  void optionsEndpoint_level1_basicFeaturesOnly() throws Exception {
    // Given: Level 1 with only basic features
    when(vcProperties.getLevel()).thenReturn(1);
    when(vcProperties.isCommitsEnabled()).thenReturn(true);
    when(vcProperties.isBranchesEnabled()).thenReturn(true);
    when(vcProperties.isHistoryEnabled()).thenReturn(true);
    when(vcProperties.isRdfPatchEnabled()).thenReturn(true);
    when(vcProperties.isMergeEnabled()).thenReturn(false);
    when(vcProperties.isConflictDetectionEnabled()).thenReturn(false);
    when(vcProperties.isTagsEnabled()).thenReturn(false);
    when(vcProperties.isRevertEnabled()).thenReturn(false);
    when(vcProperties.isResetEnabled()).thenReturn(false);
    when(vcProperties.isCherryPickEnabled()).thenReturn(false);
    when(vcProperties.isBlameEnabled()).thenReturn(false);

    // When & Then
    mockMvc.perform(options("/sparql"))
        .andExpect(status().isOk())
        .andExpect(header().string("SPARQL-Version-Control", "1.0"))
        .andExpect(header().string("SPARQL-VC-Level", "1"))
        .andExpect(header().string(HttpHeaders.ACCEPT_PATCH, "text/rdf-patch"))
        .andExpect(header().string("SPARQL-VC-Features", "commits, branches, history, rdf-patch"));
  }

  @Test
  void optionsEndpoint_level2_selectiveFeaturesDisabled() throws Exception {
    // Given: Level 2 with some features disabled
    when(vcProperties.getLevel()).thenReturn(2);
    when(vcProperties.isCommitsEnabled()).thenReturn(true);
    when(vcProperties.isBranchesEnabled()).thenReturn(true);
    when(vcProperties.isHistoryEnabled()).thenReturn(true);
    when(vcProperties.isRdfPatchEnabled()).thenReturn(true);
    when(vcProperties.isMergeEnabled()).thenReturn(true);
    when(vcProperties.isConflictDetectionEnabled()).thenReturn(true);
    when(vcProperties.isTagsEnabled()).thenReturn(false); // disabled
    when(vcProperties.isRevertEnabled()).thenReturn(true);
    when(vcProperties.isResetEnabled()).thenReturn(true);
    when(vcProperties.isCherryPickEnabled()).thenReturn(false); // disabled
    when(vcProperties.isBlameEnabled()).thenReturn(false); // disabled

    // When & Then
    mockMvc.perform(options("/sparql"))
        .andExpect(status().isOk())
        .andExpect(header().string("SPARQL-VC-Level", "2"))
        .andExpect(header().string("SPARQL-VC-Features",
            "commits, branches, history, rdf-patch, merge, conflict-detection, revert, reset"));
  }

  @Test
  void optionsEndpoint_rdfPatchDisabled_noAcceptPatchHeader() throws Exception {
    // Given: RDF Patch disabled
    when(vcProperties.getLevel()).thenReturn(1);
    when(vcProperties.isCommitsEnabled()).thenReturn(true);
    when(vcProperties.isBranchesEnabled()).thenReturn(true);
    when(vcProperties.isHistoryEnabled()).thenReturn(true);
    when(vcProperties.isRdfPatchEnabled()).thenReturn(false);
    when(vcProperties.isMergeEnabled()).thenReturn(false);
    when(vcProperties.isConflictDetectionEnabled()).thenReturn(false);
    when(vcProperties.isTagsEnabled()).thenReturn(false);
    when(vcProperties.isRevertEnabled()).thenReturn(false);
    when(vcProperties.isResetEnabled()).thenReturn(false);
    when(vcProperties.isCherryPickEnabled()).thenReturn(false);
    when(vcProperties.isBlameEnabled()).thenReturn(false);

    // When & Then
    mockMvc.perform(options("/sparql"))
        .andExpect(status().isOk())
        .andExpect(header().string("SPARQL-VC-Level", "1"))
        .andExpect(header().doesNotExist(HttpHeaders.ACCEPT_PATCH))
        .andExpect(header().string("SPARQL-VC-Features", "commits, branches, history"));
  }

  @Test
  void postEndpoint_acceptsMessageHeader() throws Exception {
    // When & Then: SPARQL-VC-Message header is accepted
    mockMvc.perform(post("/sparql")
            .contentType("application/sparql-update")
            .header("SPARQL-VC-Message", "Test commit message")
            .header("SPARQL-VC-Author", "test@example.org")
            .content("INSERT DATA { <http://example.org/s> <http://example.org/p> \"o\" }"))
        .andExpect(status().isNotImplemented());
  }

  @Test
  void postEndpoint_acceptsAuthorHeader() throws Exception {
    // When & Then: SPARQL-VC-Author header is accepted
    mockMvc.perform(post("/sparql")
            .contentType("application/sparql-update")
            .header("SPARQL-VC-Author", "alice@example.org")
            .header("SPARQL-VC-Message", "Test message")
            .content("INSERT DATA { <http://example.org/s> <http://example.org/p> \"o\" }"))
        .andExpect(status().isNotImplemented());
  }

  @Test
  void getEndpoint_acceptsVcCommitHeader() throws Exception {
    // When & Then: SPARQL-VC-Commit header is accepted
    mockMvc.perform(get("/sparql")
            .param("query", "SELECT * WHERE { ?s ?p ?o }")
            .header("SPARQL-VC-Commit", "01936f7e-1234-7890-abcd-ef0123456789"))
        .andExpect(status().isNotImplemented());
  }
}
