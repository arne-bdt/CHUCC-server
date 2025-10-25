package org.chucc.vcserver.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.chucc.vcserver.command.CreateTagCommandHandler;
import org.chucc.vcserver.dto.TagDetailResponse;
import org.chucc.vcserver.exception.TagDeletionForbiddenException;
import org.chucc.vcserver.exception.TagNotFoundException;
import org.chucc.vcserver.service.TagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Unit tests for TagController.
 */
@WebMvcTest(TagController.class)
class TagControllerTest {

  private static final String TAG_NAME = "v1.0.0";
  private static final String COMMIT_ID = "01936c7f-8a2e-7890-abcd-ef1234567890";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private TagService tagService;

  @MockitoBean
  private CreateTagCommandHandler createTagCommandHandler;

  /**
   * Test GET /version/tags/{name} with existing tag.
   */
  @Test
  void testGetTag_whenTagExists_returns200WithDetails() throws Exception {
    TagDetailResponse response = new TagDetailResponse();
    response.setName(TAG_NAME);
    response.setTarget(COMMIT_ID);
    response.setMessage("Release version 1.0.0");
    response.setAuthor("Release Manager <release@example.org>");
    response.setTimestamp("2025-01-10T14:30:00Z");

    when(tagService.getTagDetails("ds", TAG_NAME))
        .thenReturn(Optional.of(response));

    mockMvc.perform(get("/ds/version/tags/{name}", TAG_NAME)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(TAG_NAME))
        .andExpect(jsonPath("$.target").value(COMMIT_ID))
        .andExpect(jsonPath("$.message").value("Release version 1.0.0"))
        .andExpect(jsonPath("$.author").value("Release Manager <release@example.org>"))
        .andExpect(jsonPath("$.timestamp").value("2025-01-10T14:30:00Z"));
  }

  /**
   * Test GET /version/tags/{name} with non-existent tag.
   */
  @Test
  void testGetTag_whenTagDoesNotExist_returns404() throws Exception {
    when(tagService.getTagDetails("ds", TAG_NAME))
        .thenReturn(Optional.empty());

    mockMvc.perform(get("/ds/version/tags/{name}", TAG_NAME)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("tag_not_found"));
  }

  /**
   * Test DELETE /version/tags/{name} with existing tag.
   */
  @Test
  void testDeleteTag_whenTagExists_returns204() throws Exception {
    mockMvc.perform(delete("/ds/version/tags/{name}", TAG_NAME))
        .andExpect(status().isAccepted());

    verify(tagService).deleteTag("ds", TAG_NAME);
  }

  /**
   * Test DELETE /version/tags/{name} with non-existent tag.
   */
  @Test
  void testDeleteTag_whenTagDoesNotExist_returns404() throws Exception {
    doThrow(new TagNotFoundException(TAG_NAME))
        .when(tagService).deleteTag("ds", TAG_NAME);

    mockMvc.perform(delete("/ds/version/tags/{name}", TAG_NAME))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("tag_not_found"));
  }

  /**
   * Test DELETE /version/tags/{name} when policy forbids deletion.
   */
  @Test
  void testDeleteTag_whenPolicyForbids_returns403() throws Exception {
    doThrow(new TagDeletionForbiddenException())
        .when(tagService).deleteTag("ds", TAG_NAME);

    mockMvc.perform(delete("/ds/version/tags/{name}", TAG_NAME))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("tag_deletion_forbidden"));
  }
}
