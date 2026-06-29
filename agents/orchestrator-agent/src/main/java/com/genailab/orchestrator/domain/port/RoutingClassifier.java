package com.genailab.orchestrator.domain.port;

import com.genailab.orchestrator.domain.model.OrchestrationRequest;
import com.genailab.orchestrator.domain.model.RoutingTarget;

public interface RoutingClassifier {

  RoutingTarget classify(OrchestrationRequest request);
}
