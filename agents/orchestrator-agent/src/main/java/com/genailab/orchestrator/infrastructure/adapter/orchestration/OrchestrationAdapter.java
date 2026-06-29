package com.genailab.orchestrator.infrastructure.adapter.orchestration;

import com.genailab.orchestrator.domain.model.OrchestrationRequest;
import com.genailab.orchestrator.domain.model.OrchestrationResult;
import com.genailab.orchestrator.domain.model.RoutingTarget;
import com.genailab.orchestrator.domain.port.OrchestrationPort;
import com.genailab.orchestrator.domain.port.RoutingClassifier;
import com.genailab.orchestrator.infrastructure.adapter.http.DownstreamAgentClient;
import org.springframework.stereotype.Component;

@Component
public class OrchestrationAdapter implements OrchestrationPort {

  private final RoutingClassifier routingClassifier;
  private final DownstreamAgentClient downstreamAgentClient;

  public OrchestrationAdapter(
      RoutingClassifier routingClassifier,
      DownstreamAgentClient downstreamAgentClient) {
    this.routingClassifier = routingClassifier;
    this.downstreamAgentClient = downstreamAgentClient;
  }

  @Override
  public OrchestrationResult orchestrate(OrchestrationRequest request) {
    return invoke(request, routingClassifier.classify(request));
  }

  @Override
  public OrchestrationResult invoke(OrchestrationRequest request, RoutingTarget target) {
    String answer = switch (target) {
      case DOC_QUERY -> downstreamAgentClient.callDocQueryAgent(request.question());
      case DIAGNOSIS -> downstreamAgentClient.callDiagnosisAgent(request.question());
      case SMART_SEARCH -> downstreamAgentClient.callSmartSearchAgent(request.question());
    };
    return new OrchestrationResult(answer, target.agentName());
  }
}
