package com.genailab.orchestrator.infrastructure.adapter.rest;

import com.genailab.orchestrator.application.usecase.OrchestrateUseCase;
import com.genailab.orchestrator.domain.model.OrchestrationRequest;
import com.genailab.orchestrator.domain.model.OrchestrationResult;
import com.genailab.orchestrator.domain.model.RoutingTarget;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrchestratorController {

  private static final String BYPASS_HEADER = "X-Bypass-Routing";

  private final OrchestrateUseCase useCase;

  public OrchestratorController(OrchestrateUseCase useCase) {
    this.useCase = useCase;
  }

  @PostMapping("/ask")
  public ResponseEntity<AskResponseDto> ask(
      @Valid @RequestBody AskRequestDto request,
      @RequestHeader(value = BYPASS_HEADER, required = false) String bypassRouting) {
    OrchestrationRequest orchestrationRequest = new OrchestrationRequest(request.question());
    OrchestrationResult result = hasBypass(bypassRouting)
        ? useCase.bypass(orchestrationRequest, RoutingTarget.fromHeader(bypassRouting))
        : useCase.orchestrate(orchestrationRequest);
    return ResponseEntity.ok(new AskResponseDto(result.answer(), result.routedTo()));
  }

  private boolean hasBypass(String bypassRouting) {
    return bypassRouting != null && !bypassRouting.isBlank();
  }
}
