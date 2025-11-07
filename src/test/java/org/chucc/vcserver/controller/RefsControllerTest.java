package org.chucc.vcserver.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collections;
import java.util.List;
import org.chucc.vcserver.dto.RefResponse;
import org.chucc.vcserver.service.RefService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Unit tests for RefsController.
 */
@WebMvcTest(RefsController.class)
class RefsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private RefService refService;

  @MockitoBean
  private MeterRegistry meterRegistry;

  private static final String DATASET_NAME = "test-dataset";

  @Test
  void listRefs_shouldReturnEmptyArray_whenNoRefs() throws Exception {
    // Given
    when(refService.getAllRefs(DATASET_NAME)).thenReturn(Collections.emptyList());

    // When & Then
    mockMvc.perform(get("/" + DATASET_NAME + "/version/refs"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.refs").isArray())
        .andExpect(jsonPath("$.refs").isEmpty());
  }

  @Test
  void listRefs_shouldReturnBranches() throws Exception {
    // Given
    RefResponse branch1 = new RefResponse("branch", "main",
        "01936c7f-8a2e-7890-abcd-ef1234567890");
    RefResponse branch2 = new RefResponse("branch", "develop",
        "01936c80-1234-7890-abcd-ef1234567890");

    when(refService.getAllRefs(DATASET_NAME)).thenReturn(List.of(branch1, branch2));

    // When & Then
    mockMvc.perform(get("/" + DATASET_NAME + "/version/refs"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.refs").isArray())
        .andExpect(jsonPath("$.refs.length()").value(2))
        .andExpect(jsonPath("$.refs[0].type").value("branch"))
        .andExpect(jsonPath("$.refs[0].name").value("main"))
        .andExpect(jsonPath("$.refs[0].targetCommit").value("01936c7f-8a2e-7890-abcd-ef1234567890"))
        .andExpect(jsonPath("$.refs[1].type").value("branch"))
        .andExpect(jsonPath("$.refs[1].name").value("develop"));
  }

  @Test
  void listRefs_shouldReturnTags() throws Exception {
    // Given
    RefResponse tag = new RefResponse("tag", "v1.0.0",
        "01936c7f-8a2e-7890-abcd-ef1234567890");

    when(refService.getAllRefs(DATASET_NAME)).thenReturn(List.of(tag));

    // When & Then
    mockMvc.perform(get("/" + DATASET_NAME + "/version/refs"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.refs").isArray())
        .andExpect(jsonPath("$.refs.length()").value(1))
        .andExpect(jsonPath("$.refs[0].type").value("tag"))
        .andExpect(jsonPath("$.refs[0].name").value("v1.0.0"))
        .andExpect(jsonPath("$.refs[0].targetCommit").value("01936c7f-8a2e-7890-abcd-ef1234567890"));
  }

  @Test
  void listRefs_shouldReturnMixedBranchesAndTags() throws Exception {
    // Given
    RefResponse branch = new RefResponse("branch", "main",
        "01936c7f-8a2e-7890-abcd-ef1234567890");
    RefResponse tag = new RefResponse("tag", "v1.0.0",
        "01936c80-1234-7890-abcd-ef1234567890", "Release version 1.0.0");

    when(refService.getAllRefs(DATASET_NAME)).thenReturn(List.of(branch, tag));

    // When & Then
    mockMvc.perform(get("/" + DATASET_NAME + "/version/refs"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.refs").isArray())
        .andExpect(jsonPath("$.refs.length()").value(2))
        .andExpect(jsonPath("$.refs[0].type").value("branch"))
        .andExpect(jsonPath("$.refs[1].type").value("tag"))
        .andExpect(jsonPath("$.refs[1].message").value("Release version 1.0.0"));
  }

  @Test
  void listRefs_shouldNotIncludeNullMessageField_whenNotSet() throws Exception {
    // Given
    RefResponse branch = new RefResponse("branch", "main",
        "01936c7f-8a2e-7890-abcd-ef1234567890");

    when(refService.getAllRefs(DATASET_NAME)).thenReturn(List.of(branch));

    // When & Then
    mockMvc.perform(get("/" + DATASET_NAME + "/version/refs"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.refs[0].message").doesNotExist());
  }

  @Test
  void listRefs_shouldUseDefaultDataset_whenParamNotProvided() throws Exception {
    // Given - using default dataset name
    when(refService.getAllRefs("default")).thenReturn(Collections.emptyList());

    // When & Then
    mockMvc.perform(get("/default/version/refs"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.refs").isArray());
  }
}
