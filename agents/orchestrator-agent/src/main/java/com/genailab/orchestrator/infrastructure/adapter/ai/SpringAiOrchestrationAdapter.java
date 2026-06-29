package com.genailab.orchestrator.infrastructure.adapter.ai;

import com.genailab.orchestrator.domain.model.OrchestrationRequest;
import com.genailab.orchestrator.domain.model.OrchestrationResult;
import com.genailab.orchestrator.domain.model.RoutingTarget;
import com.genailab.orchestrator.domain.port.OrchestrationPort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class SpringAiOrchestrationAdapter implements OrchestrationPort {

  private final ChatClient chatClient;
  private final AgentTools agentTools;

  public SpringAiOrchestrationAdapter(ChatClient chatClient, AgentTools agentTools) {
    this.chatClient = chatClient;
    this.agentTools = agentTools;
  }

  @Override
  public OrchestrationResult orchestrate(OrchestrationRequest request) {
    agentTools.clearRoutedTo();
    String answer = chatClient.prompt()
        .user(request.question())
        .tools(agentTools)
        .call()
        .content();
    return new OrchestrationResult(answer, firstRoutedAgent());
  }

  @Override
  public OrchestrationResult invoke(OrchestrationRequest request, RoutingTarget target) {
    agentTools.clearRoutedTo();
    String answer = switch (target) {
      case DOC_QUERY -> agentTools.callDocQueryAgent(request.question());
      case DIAGNOSIS -> agentTools.callDiagnosisAgent(request.question());
      case SMART_SEARCH -> agentTools.callSmartSearchAgent(request.question());
    };
    return new OrchestrationResult(answer, target.agentName());
  }

  private String firstRoutedAgent() {
    return agentTools.getRoutedTo().isEmpty() ? null : agentTools.getRoutedTo().getFirst();
  }
}
