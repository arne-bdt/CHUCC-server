package org.chucc.vcserver.controller;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.chucc.vcserver.command.SparqlUpdateCommandHandler;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.ResultFormat;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.SelectorResolutionService;
import org.chucc.vcserver.service.SparqlQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

  @MockitoBean
  private SelectorResolutionService selectorResolutionService;

  @MockitoBean
  private DatasetService datasetService;

  @MockitoBean
  private SparqlQueryService sparqlQueryService;

  @MockitoBean
  private SparqlUpdateCommandHandler sparqlUpdateCommandHandler;

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
    // Given: Mock handler returns successful commit
    String commitIdValue = CommitId.generate().value();
    CommitCreatedEvent event = new CommitCreatedEvent(
        "default", commitIdValue, java.util.List.of(), "main",
        "Test commit message", "test@example.org",
        java.time.Instant.now(), "");
    when(sparqlUpdateCommandHandler.handle(any())).thenReturn(event);

    // When & Then: SPARQL-VC-Message header is accepted and update succeeds
    mockMvc.perform(post("/sparql")
            .contentType("application/sparql-update")
            .header("SPARQL-VC-Message", "Test commit message")
            .header("SPARQL-VC-Author", "test@example.org")
            .content("INSERT DATA { <http://example.org/s> <http://example.org/p> \"o\" }"))
        .andExpect(status().isOk())
        .andExpect(header().exists("ETag"))
        .andExpect(header().exists("Location"));
  }

  @Test
  void postEndpoint_acceptsAuthorHeader() throws Exception {
    // Given: Mock handler returns successful commit
    String commitIdValue = CommitId.generate().value();
    CommitCreatedEvent event = new CommitCreatedEvent(
        "default", commitIdValue, java.util.List.of(), "main",
        "Test message", "alice@example.org",
        java.time.Instant.now(), "");
    when(sparqlUpdateCommandHandler.handle(any())).thenReturn(event);

    // When & Then: SPARQL-VC-Author header is accepted and update succeeds
    mockMvc.perform(post("/sparql")
            .contentType("application/sparql-update")
            .header("SPARQL-VC-Author", "alice@example.org")
            .header("SPARQL-VC-Message", "Test message")
            .content("INSERT DATA { <http://example.org/s> <http://example.org/p> \"o\" }"))
        .andExpect(status().isOk())
        .andExpect(header().exists("ETag"))
        .andExpect(header().exists("Location"));
  }

  @Test
  void getEndpoint_acceptsVcCommitHeader() throws Exception {
    // Given: Mocked services return valid data
    CommitId commitId = CommitId.generate();
    Dataset dataset = DatasetFactory.create();
    String results = "{\"head\":{},\"results\":{\"bindings\":[]}}";

    when(selectorResolutionService.resolve(anyString(), any(), any(), any()))
        .thenReturn(commitId);
    when(datasetService.materializeAtCommit(anyString(), any(CommitId.class)))
        .thenReturn(dataset);
    when(sparqlQueryService.executeQuery(any(Dataset.class), anyString(), any(ResultFormat.class)))
        .thenReturn(results);

    // When & Then: SPARQL-VC-Commit header is accepted and query succeeds
    mockMvc.perform(get("/sparql")
            .param("query", "SELECT * WHERE { ?s ?p ?o }")
            .header("SPARQL-VC-Commit", "01936f7e-1234-7890-abcd-ef0123456789"))
        .andExpect(status().isOk());
  }
}
