package com.genailab.smartsearch.infrastructure.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.genailab.smartsearch.application.usecase.ChatUseCase;
import com.genailab.smartsearch.domain.model.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ChatUseCase useCase;

  @Test
  void shouldReturnBadRequestWhenQuestionIsBlank() throws Exception {
    mockMvc.perform(post("/api/v1/chat")
            .contentType("application/json")
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
  }

  @Test
  void shouldReturnAnswerAndToolsUsed() throws Exception {
    when(useCase.chat(any())).thenReturn(
        new ChatResponse("Las entidades tienen identidad única.", List.of("searchDocs")));

    mockMvc.perform(post("/api/v1/chat")
            .contentType("application/json")
            .content("{\"question\":\"¿Qué son las entidades?\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer").value("Las entidades tienen identidad única."))
        .andExpect(jsonPath("$.toolsUsed[0]").value("searchDocs"));
  }

  @Test
  void shouldReturnEmptyToolsUsedWhenNoToolWasInvoked() throws Exception {
    when(useCase.chat(any())).thenReturn(
        new ChatResponse("Hola, ¿en qué puedo ayudarte?", List.of()));

    mockMvc.perform(post("/api/v1/chat")
            .contentType("application/json")
            .content("{\"question\":\"Hola\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.toolsUsed").isArray())
        .andExpect(jsonPath("$.toolsUsed").isEmpty());
  }
}
