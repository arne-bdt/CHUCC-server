package org.chucc.vcserver.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.Instant;
import java.util.List;
import org.chucc.vcserver.command.CreateCommitCommand;
import org.chucc.vcserver.command.CreateCommitCommandHandler;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.service.PreconditionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Unit tests for CommitController POST endpoint.
 */
@WebMvcTest(CommitController.class)
class CommitControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private CreateCommitCommandHandler createCommitCommandHandler;

  @MockitoBean
  private PreconditionService preconditionService;

  @MockitoBean
  private org.chucc.vcserver.service.SelectorResolutionService selectorResolutionService;

  private static final String DATASET_NAME = "test-dataset";
  private static final String COMMIT_ID = "01936c81-4567-7890-abcd-ef1234567890";
  private static final String PARENT_COMMIT_ID = "01936c7f-8a2e-7890-abcd-ef1234567890";
  private static final String PATCH_BODY = "TX .\nA <http://example.org/s> "
      + "<http://example.org/p> \"value\" .\nTC .";

  @Test
  void createCommit_shouldReturn201_whenValidPatchOnBranch() throws Exception {
    // Given
    CommitCreatedEvent event = new CommitCreatedEvent(
        DATASET_NAME,
        COMMIT_ID,
        List.of(PARENT_COMMIT_ID), null,
        "Add new triple",
        "Alice <mailto:alice@example.org>",
        Instant.parse("2025-01-15T10:30:00Z"),
        PATCH_BODY
    );

    when(createCommitCommandHandler.handle(any(CreateCommitCommand.class)))
        .thenReturn(event);

    // When & Then
    mockMvc.perform(post("/version/commits")
            .param("branch", "main")
            .param("dataset", DATASET_NAME)
            .contentType("text/rdf-patch")
            .header("SPARQL-VC-Author", "Alice <mailto:alice@example.org>")
            .header("SPARQL-VC-Message", "Add new triple")
            .content(PATCH_BODY))
        .andExpect(status().isAccepted())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().string("Location", "/version/commits/" + COMMIT_ID))
        .andExpect(header().string("ETag", "\"" + COMMIT_ID + "\""))
        .andExpect(jsonPath("$.id").value(COMMIT_ID))
        .andExpect(jsonPath("$.parents[0]").value(PARENT_COMMIT_ID))
        .andExpect(jsonPath("$.author").value("Alice <mailto:alice@example.org>"))
        .andExpect(jsonPath("$.message").value("Add new triple"))
        .andExpect(jsonPath("$.timestamp").value("2025-01-15T10:30:00Z"));
  }

  @Test
  void createCommit_shouldReturn201_whenValidPatchOnDetachedCommit() throws Exception {
    // Given
    CommitCreatedEvent event = new CommitCreatedEvent(
        DATASET_NAME,
        COMMIT_ID,
        List.of(PARENT_COMMIT_ID), null,
        "Experimental change",
        "Bob",
        Instant.parse("2025-01-15T11:00:00Z"),
        PATCH_BODY
    );

    when(createCommitCommandHandler.handle(any(CreateCommitCommand.class)))
        .thenReturn(event);

    // When & Then
    mockMvc.perform(post("/version/commits")
            .param("commit", PARENT_COMMIT_ID)
            .param("dataset", DATASET_NAME)
            .contentType("text/rdf-patch")
            .header("SPARQL-VC-Author", "Bob")
            .header("SPARQL-VC-Message", "Experimental change")
            .content(PATCH_BODY))
        .andExpect(status().isAccepted())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().string("Location", "/version/commits/" + COMMIT_ID))
        .andExpect(header().string("ETag", "\"" + COMMIT_ID + "\""))
        .andExpect(jsonPath("$.id").value(COMMIT_ID));
  }

  @Test
  void createCommit_shouldReturn204_whenNoOpPatch() throws Exception {
    // Given - handler returns null for no-op patch
    when(createCommitCommandHandler.handle(any(CreateCommitCommand.class)))
        .thenReturn(null);

    // When & Then
    mockMvc.perform(post("/version/commits")
            .param("branch", "main")
            .param("dataset", DATASET_NAME)
            .contentType("text/rdf-patch")
            .header("SPARQL-VC-Author", "Alice")
            .header("SPARQL-VC-Message", "No-op change")
            .content(PATCH_BODY))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
  }

  @Test
  void createCommit_shouldReturn400_whenBothBranchAndCommitProvided() throws Exception {
    // When & Then
    mockMvc.perform(post("/version/commits")
            .param("branch", "main")
            .param("commit", PARENT_COMMIT_ID)
            .param("dataset", DATASET_NAME)
            .contentType("text/rdf-patch")
            .content(PATCH_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType("application/problem+json"));

    // Verify handler was not called
    verify(createCommitCommandHandler, never()).handle(any());
  }

  @Test
  void createCommit_shouldReturn400_whenNeitherBranchNorCommitProvided() throws Exception {
    // When & Then
    mockMvc.perform(post("/version/commits")
            .param("dataset", DATASET_NAME)
            .contentType("text/rdf-patch")
            .content(PATCH_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType("application/problem+json"));

    // Verify handler was not called
    verify(createCommitCommandHandler, never()).handle(any());
  }

  @Test
  void createCommit_shouldReturn400_whenAsOfWithCommitSelector() throws Exception {
    // When & Then
    mockMvc.perform(post("/version/commits")
            .param("commit", PARENT_COMMIT_ID)
            .param("asOf", "2025-01-01T00:00:00Z")
            .param("dataset", DATASET_NAME)
            .contentType("text/rdf-patch")
            .content(PATCH_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType("application/problem+json"));

    // Verify handler was not called
    verify(createCommitCommandHandler, never()).handle(any());
  }

  @Test
  void createCommit_shouldReturn415_whenInvalidContentType() throws Exception {
    // When & Then
    mockMvc.perform(post("/version/commits")
            .param("branch", "main")
            .param("dataset", DATASET_NAME)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isUnsupportedMediaType());

    // Verify handler was not called
    verify(createCommitCommandHandler, never()).handle(any());
  }

  @Test
  void createCommit_shouldAllowAsOfWithBranch() throws Exception {
    // Given
    CommitCreatedEvent event = new CommitCreatedEvent(
        DATASET_NAME,
        COMMIT_ID,
        List.of(PARENT_COMMIT_ID), null,
        "Historical commit",
        "Alice",
        Instant.parse("2025-01-15T10:30:00Z"),
        PATCH_BODY
    );

    when(createCommitCommandHandler.handle(any(CreateCommitCommand.class)))
        .thenReturn(event);

    // When & Then
    mockMvc.perform(post("/version/commits")
            .param("branch", "main")
            .param("asOf", "2025-01-01T00:00:00Z")
            .param("dataset", DATASET_NAME)
            .contentType("text/rdf-patch")
            .header("SPARQL-VC-Author", "Alice")
            .header("SPARQL-VC-Message", "Historical commit")
            .content(PATCH_BODY))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.id").value(COMMIT_ID));
  }

  @Test
  void createCommit_shouldUseDefaultDataset_whenNotProvided() throws Exception {
    // Given
    CommitCreatedEvent event = new CommitCreatedEvent(
        "default",
        COMMIT_ID,
        List.of(PARENT_COMMIT_ID), null,
        "Add new triple",
        "Alice",
        Instant.parse("2025-01-15T10:30:00Z"),
        PATCH_BODY
    );

    when(createCommitCommandHandler.handle(any(CreateCommitCommand.class)))
        .thenReturn(event);

    // When & Then
    mockMvc.perform(post("/version/commits")
            .param("branch", "main")
            .contentType("text/rdf-patch")
            .header("SPARQL-VC-Author", "Alice")
            .header("SPARQL-VC-Message", "Add new triple")
            .content(PATCH_BODY))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.id").value(COMMIT_ID));
  }
}
