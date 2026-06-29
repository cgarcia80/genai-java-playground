package com.genailab.orchestrator.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genailab.orchestrator.domain.model.OrchestrationRequest;
import com.genailab.orchestrator.domain.model.OrchestrationResult;
import com.genailab.orchestrator.domain.model.RoutingTarget;
import com.genailab.orchestrator.domain.port.OrchestrationPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrchestrateUseCaseTest {

  @Mock
  private OrchestrationPort port;

  @InjectMocks
  private OrchestrateUseCase useCase;

  @Test
  void shouldDelegateOrchestrationToPort() {
    OrchestrationRequest request = new OrchestrationRequest("Que son las entidades?");
    OrchestrationResult expected =
        new OrchestrationResult("Las entidades son...", "doc-query-agent");
    when(port.orchestrate(request)).thenReturn(expected);

    OrchestrationResult result = useCase.orchestrate(request);

    assertThat(result).isEqualTo(expected);
    verify(port).orchestrate(request);
  }

  @Test
  void shouldDelegateBypassToPort() {
    OrchestrationRequest request = new OrchestrationRequest("Diagnostica este error");
    OrchestrationResult expected =
        new OrchestrationResult("Root cause: null value", "diagnosis-agent");
    when(port.invoke(request, RoutingTarget.DIAGNOSIS)).thenReturn(expected);

    OrchestrationResult result = useCase.bypass(request, RoutingTarget.DIAGNOSIS);

    assertThat(result).isEqualTo(expected);
    verify(port).invoke(request, RoutingTarget.DIAGNOSIS);
  }
}
