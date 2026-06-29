package com.genailab.orchestrator.domain.port;

import com.genailab.orchestrator.domain.model.OrchestrationRequest;
import com.genailab.orchestrator.domain.model.OrchestrationResult;
import com.genailab.orchestrator.domain.model.RoutingTarget;

public interface OrchestrationPort {

  OrchestrationResult orchestrate(OrchestrationRequest request);

  OrchestrationResult invoke(OrchestrationRequest request, RoutingTarget target);
}
