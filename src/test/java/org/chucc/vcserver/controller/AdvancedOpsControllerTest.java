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
import org.chucc.vcserver.command.CherryPickCommandHandler;
import org.chucc.vcserver.command.ResetBranchCommand;
import org.chucc.vcserver.command.ResetBranchCommandHandler;
import org.chucc.vcserver.event.BranchResetEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Unit tests for AdvancedOpsController.
 */
@WebMvcTest(AdvancedOpsController.class)
class AdvancedOpsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ResetBranchCommandHandler resetBranchCommandHandler;

  @MockitoBean
  private CherryPickCommandHandler cherryPickCommandHandler;

  private static final String DATASET_NAME = "test-dataset";
  private static final String BRANCH_NAME = "main";
  private static final String OLD_COMMIT_ID = "01936c7f-8a2e-7890-abcd-ef1234567890";
  private static final String NEW_COMMIT_ID = "01936c81-4567-7890-abcd-ef1234567890";

  @Test
  void resetBranch_shouldReturn200_whenValidHardReset() throws Exception {
    // Given
    BranchResetEvent event = new BranchResetEvent(
        DATASET_NAME,
        BRANCH_NAME,
        OLD_COMMIT_ID,
        NEW_COMMIT_ID,
        Instant.parse("2025-01-15T10:30:00Z")
    );

    when(resetBranchCommandHandler.handle(any(ResetBranchCommand.class)))
        .thenReturn(event);

    String requestBody = """
        {
          "branch": "main",
          "to": "01936c81-4567-7890-abcd-ef1234567890",
          "mode": "HARD"
        }
        """;

    // When & Then
    mockMvc.perform(post("/version/reset")
            .param("dataset", DATASET_NAME)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().string("ETag", "\"" + NEW_COMMIT_ID + "\""))
        .andExpect(jsonPath("$.branch").value(BRANCH_NAME))
        .andExpect(jsonPath("$.newHead").value(NEW_COMMIT_ID))
        .andExpect(jsonPath("$.previousHead").value(OLD_COMMIT_ID));
  }

  @Test
  void resetBranch_shouldReturn200_whenValidSoftReset() throws Exception {
    // Given
    BranchResetEvent event = new BranchResetEvent(
        DATASET_NAME,
        BRANCH_NAME,
        OLD_COMMIT_ID,
        NEW_COMMIT_ID,
        Instant.now()
    );

    when(resetBranchCommandHandler.handle(any(ResetBranchCommand.class)))
        .thenReturn(event);

    String requestBody = """
        {
          "branch": "main",
          "to": "01936c81-4567-7890-abcd-ef1234567890",
          "mode": "SOFT"
        }
        """;

    // When & Then
    mockMvc.perform(post("/version/reset")
            .param("dataset", DATASET_NAME)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.branch").value(BRANCH_NAME))
        .andExpect(jsonPath("$.newHead").value(NEW_COMMIT_ID))
        .andExpect(jsonPath("$.previousHead").value(OLD_COMMIT_ID));
  }

  @Test
  void resetBranch_shouldReturn200_whenValidMixedReset() throws Exception {
    // Given
    BranchResetEvent event = new BranchResetEvent(
        DATASET_NAME,
        BRANCH_NAME,
        OLD_COMMIT_ID,
        NEW_COMMIT_ID,
        Instant.now()
    );

    when(resetBranchCommandHandler.handle(any(ResetBranchCommand.class)))
        .thenReturn(event);

    String requestBody = """
        {
          "branch": "main",
          "to": "01936c81-4567-7890-abcd-ef1234567890",
          "mode": "MIXED"
        }
        """;

    // When & Then
    mockMvc.perform(post("/version/reset")
            .param("dataset", DATASET_NAME)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.branch").value(BRANCH_NAME))
        .andExpect(jsonPath("$.newHead").value(NEW_COMMIT_ID))
        .andExpect(jsonPath("$.previousHead").value(OLD_COMMIT_ID));
  }

  @Test
  void resetBranch_shouldReturn400_whenBranchNameMissing() throws Exception {
    // Given
    String requestBody = """
        {
          "to": "01936c81-4567-7890-abcd-ef1234567890",
          "mode": "HARD"
        }
        """;

    // When & Then
    mockMvc.perform(post("/version/reset")
            .param("dataset", DATASET_NAME)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType("application/problem+json"));

    // Verify handler was not called
    verify(resetBranchCommandHandler, never()).handle(any());
  }

  @Test
  void resetBranch_shouldReturn400_whenTargetCommitMissing() throws Exception {
    // Given
    String requestBody = """
        {
          "branch": "main",
          "mode": "HARD"
        }
        """;

    // When & Then
    mockMvc.perform(post("/version/reset")
            .param("dataset", DATASET_NAME)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType("application/problem+json"));

    // Verify handler was not called
    verify(resetBranchCommandHandler, never()).handle(any());
  }

  @Test
  void resetBranch_shouldReturn400_whenModeMissing() throws Exception {
    // Given
    String requestBody = """
        {
          "branch": "main",
          "to": "01936c81-4567-7890-abcd-ef1234567890"
        }
        """;

    // When & Then
    mockMvc.perform(post("/version/reset")
            .param("dataset", DATASET_NAME)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType("application/problem+json"));

    // Verify handler was not called
    verify(resetBranchCommandHandler, never()).handle(any());
  }

  @Test
  void resetBranch_shouldReturn400_whenBranchNameBlank() throws Exception {
    // Given
    String requestBody = """
        {
          "branch": "",
          "to": "01936c81-4567-7890-abcd-ef1234567890",
          "mode": "HARD"
        }
        """;

    // When & Then
    mockMvc.perform(post("/version/reset")
            .param("dataset", DATASET_NAME)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType("application/problem+json"));

    // Verify handler was not called
    verify(resetBranchCommandHandler, never()).handle(any());
  }

  @Test
  void resetBranch_shouldReturn400_whenTargetCommitBlank() throws Exception {
    // Given
    String requestBody = """
        {
          "branch": "main",
          "to": "",
          "mode": "HARD"
        }
        """;

    // When & Then
    mockMvc.perform(post("/version/reset")
            .param("dataset", DATASET_NAME)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType("application/problem+json"));

    // Verify handler was not called
    verify(resetBranchCommandHandler, never()).handle(any());
  }

  @Test
  void resetBranch_shouldReturn404_whenBranchNotFound() throws Exception {
    // Given
    when(resetBranchCommandHandler.handle(any(ResetBranchCommand.class)))
        .thenThrow(new IllegalArgumentException("Branch not found: feature in dataset: "
            + DATASET_NAME));

    String requestBody = """
        {
          "branch": "feature",
          "to": "01936c81-4567-7890-abcd-ef1234567890",
          "mode": "HARD"
        }
        """;

    // When & Then
    mockMvc.perform(post("/version/reset")
            .param("dataset", DATASET_NAME)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType("application/problem+json"));
  }

  @Test
  void resetBranch_shouldReturn404_whenCommitNotFound() throws Exception {
    // Given
    when(resetBranchCommandHandler.handle(any(ResetBranchCommand.class)))
        .thenThrow(new IllegalArgumentException("Target commit not found: "
            + NEW_COMMIT_ID + " in dataset: " + DATASET_NAME));

    String requestBody = """
        {
          "branch": "main",
          "to": "01936c81-4567-7890-abcd-ef1234567890",
          "mode": "HARD"
        }
        """;

    // When & Then
    mockMvc.perform(post("/version/reset")
            .param("dataset", DATASET_NAME)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType("application/problem+json"));
  }

  @Test
  void resetBranch_shouldUseDefaultDataset_whenNotProvided() throws Exception {
    // Given
    BranchResetEvent event = new BranchResetEvent(
        "default",
        BRANCH_NAME,
        OLD_COMMIT_ID,
        NEW_COMMIT_ID,
        Instant.now()
    );

    when(resetBranchCommandHandler.handle(any(ResetBranchCommand.class)))
        .thenReturn(event);

    String requestBody = """
        {
          "branch": "main",
          "to": "01936c81-4567-7890-abcd-ef1234567890",
          "mode": "HARD"
        }
        """;

    // When & Then
    mockMvc.perform(post("/version/reset")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.branch").value(BRANCH_NAME))
        .andExpect(jsonPath("$.newHead").value(NEW_COMMIT_ID))
        .andExpect(jsonPath("$.previousHead").value(OLD_COMMIT_ID));
  }
}
