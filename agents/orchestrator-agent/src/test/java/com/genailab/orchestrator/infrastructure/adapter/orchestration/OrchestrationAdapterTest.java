package com.genailab.orchestrator.infrastructure.adapter.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genailab.orchestrator.domain.model.OrchestrationRequest;
import com.genailab.orchestrator.domain.model.OrchestrationResult;
import com.genailab.orchestrator.domain.model.RoutingTarget;
import com.genailab.orchestrator.domain.port.RoutingClassifier;
import com.genailab.orchestrator.infrastructure.adapter.http.DownstreamAgentClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrchestrationAdapterTest {

  @Mock
  private RoutingClassifier routingClassifier;

  @Mock
  private DownstreamAgentClient downstreamAgentClient;

  @InjectMocks
  private OrchestrationAdapter adapter;

  @Test
  void shouldClassifyAndInvokeSelectedAgent() {
    OrchestrationRequest request = new OrchestrationRequest("Que son las entidades?");
    when(routingClassifier.classify(request)).thenReturn(RoutingTarget.DOC_QUERY);
    when(downstreamAgentClient.callDocQueryAgent(request.question()))
        .thenReturn("Una entidad tiene identidad.");

    OrchestrationResult result = adapter.orchestrate(request);

    assertThat(result).isEqualTo(
        new OrchestrationResult("Una entidad tiene identidad.", "doc-query-agent"));
    verify(routingClassifier).classify(request);
  }

  @Test
  void shouldBypassClassifierWhenInvokingDirectly() {
    OrchestrationRequest request = new OrchestrationRequest("NullPointerException");
    when(downstreamAgentClient.callDiagnosisAgent(request.question()))
        .thenReturn("Root cause: Null value");

    OrchestrationResult result = adapter.invoke(request, RoutingTarget.DIAGNOSIS);

    assertThat(result).isEqualTo(
        new OrchestrationResult("Root cause: Null value", "diagnosis-agent"));
    verify(downstreamAgentClient).callDiagnosisAgent("NullPointerException");
  }
}
