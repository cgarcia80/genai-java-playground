package com.genailab.orchestrator.infrastructure.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.genailab.orchestrator.application.usecase.OrchestrateUseCase;
import com.genailab.orchestrator.domain.exception.DownstreamAgentException;
import com.genailab.orchestrator.domain.model.OrchestrationResult;
import com.genailab.orchestrator.domain.model.RoutingTarget;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrchestratorController.class)
class OrchestratorControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private OrchestrateUseCase useCase;

  @Test
  void shouldReturnBadRequestWhenQuestionIsBlank() throws Exception {
    mockMvc.perform(post("/api/v1/ask")
            .contentType("application/json")
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
  }

  @Test
  void shouldReturnBadRequestWhenBypassHeaderIsInvalid() throws Exception {
    mockMvc.perform(post("/api/v1/ask")
            .contentType("application/json")
            .header("X-Bypass-Routing", "unknown")
            .content("{\"question\":\"Hola\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
  }

  @Test
  void shouldReturnAnswerAndRoutedToWhenRequestIsValid() throws Exception {
    when(useCase.orchestrate(any())).thenReturn(
        new OrchestrationResult("Una entidad tiene identidad.", "doc-query-agent"));

    mockMvc.perform(post("/api/v1/ask")
            .contentType("application/json")
            .content("{\"question\":\"Que son las entidades?\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer").value("Una entidad tiene identidad."))
        .andExpect(jsonPath("$.routedTo").value("doc-query-agent"));
  }

  @Test
  void shouldReturnAnswerAndNullRoutedToWhenNoToolWasInvoked() throws Exception {
    when(useCase.orchestrate(any())).thenReturn(
        new OrchestrationResult("Hola.", null));

    mockMvc.perform(post("/api/v1/ask")
            .contentType("application/json")
            .content("{\"question\":\"Hola\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer").value("Hola."))
        .andExpect(jsonPath("$.routedTo").isEmpty());
  }

  @Test
  void shouldUseBypassWhenHeaderIsPresent() throws Exception {
    when(useCase.bypass(any(), eq(RoutingTarget.DIAGNOSIS))).thenReturn(
        new OrchestrationResult("Root cause: null value", "diagnosis-agent"));

    mockMvc.perform(post("/api/v1/ask")
            .contentType("application/json")
            .header("X-Bypass-Routing", "diagnosis")
            .content("{\"question\":\"NullPointerException\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer").value("Root cause: null value"))
        .andExpect(jsonPath("$.routedTo").value("diagnosis-agent"));
    verify(useCase).bypass(any(), eq(RoutingTarget.DIAGNOSIS));
  }

  @Test
  void shouldReturnBadGatewayWhenDownstreamFails() throws Exception {
    when(useCase.orchestrate(any())).thenThrow(new DownstreamAgentException("downstream failed"));

    mockMvc.perform(post("/api/v1/ask")
            .contentType("application/json")
            .content("{\"question\":\"Que son las entidades?\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("BAD_GATEWAY"));
  }
}
