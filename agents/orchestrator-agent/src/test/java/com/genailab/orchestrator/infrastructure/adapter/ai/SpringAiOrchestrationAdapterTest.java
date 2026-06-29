package com.genailab.orchestrator.infrastructure.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genailab.orchestrator.domain.model.OrchestrationRequest;
import com.genailab.orchestrator.domain.model.OrchestrationResult;
import com.genailab.orchestrator.domain.model.RoutingTarget;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class SpringAiOrchestrationAdapterTest {

  @Test
  void shouldReturnFirstRoutedAgentWhenToolWasInvoked() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
    AgentTools agentTools = mock(AgentTools.class);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.user("Que son las entidades?")).thenReturn(requestSpec);
    when(requestSpec.tools(agentTools)).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(responseSpec);
    when(responseSpec.content()).thenReturn("Una entidad tiene identidad.");
    when(agentTools.getRoutedTo()).thenReturn(List.of("doc-query-agent"));
    SpringAiOrchestrationAdapter adapter =
        new SpringAiOrchestrationAdapter(chatClient, agentTools);

    OrchestrationResult result =
        adapter.orchestrate(new OrchestrationRequest("Que son las entidades?"));

    assertThat(result).isEqualTo(
        new OrchestrationResult("Una entidad tiene identidad.", "doc-query-agent"));
    verify(agentTools).clearRoutedTo();
  }

  @Test
  void shouldReturnNullRoutedToWhenNoToolWasInvoked() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
    AgentTools agentTools = mock(AgentTools.class);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.user("Hola")).thenReturn(requestSpec);
    when(requestSpec.tools(agentTools)).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(responseSpec);
    when(responseSpec.content()).thenReturn("Hola.");
    when(agentTools.getRoutedTo()).thenReturn(List.of());
    SpringAiOrchestrationAdapter adapter =
        new SpringAiOrchestrationAdapter(chatClient, agentTools);

    OrchestrationResult result = adapter.orchestrate(new OrchestrationRequest("Hola"));

    assertThat(result).isEqualTo(new OrchestrationResult("Hola.", null));
  }

  @Test
  void shouldBypassChatClientWhenInvokingDirectly() {
    ChatClient chatClient = mock(ChatClient.class);
    AgentTools agentTools = mock(AgentTools.class);
    when(agentTools.callDiagnosisAgent("NullPointerException"))
        .thenReturn("Root cause: Null value");
    SpringAiOrchestrationAdapter adapter =
        new SpringAiOrchestrationAdapter(chatClient, agentTools);

    OrchestrationResult result =
        adapter.invoke(new OrchestrationRequest("NullPointerException"), RoutingTarget.DIAGNOSIS);

    assertThat(result).isEqualTo(
        new OrchestrationResult("Root cause: Null value", "diagnosis-agent"));
    verify(agentTools).clearRoutedTo();
    verify(agentTools).callDiagnosisAgent("NullPointerException");
  }
}
