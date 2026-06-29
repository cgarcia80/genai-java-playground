package com.genailab.orchestrator.application.usecase;

import com.genailab.orchestrator.domain.model.OrchestrationRequest;
import com.genailab.orchestrator.domain.model.OrchestrationResult;
import com.genailab.orchestrator.domain.model.RoutingTarget;
import com.genailab.orchestrator.domain.port.OrchestrationPort;
import org.springframework.stereotype.Service;

@Service
public class OrchestrateUseCase {

  private final OrchestrationPort port;

  public OrchestrateUseCase(OrchestrationPort port) {
    this.port = port;
  }

  public OrchestrationResult orchestrate(OrchestrationRequest request) {
    return port.orchestrate(request);
  }

  public OrchestrationResult bypass(OrchestrationRequest request, RoutingTarget target) {
    return port.invoke(request, target);
  }
}
