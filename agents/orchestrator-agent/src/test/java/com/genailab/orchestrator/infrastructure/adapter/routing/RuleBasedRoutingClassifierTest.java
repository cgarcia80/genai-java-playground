package com.genailab.orchestrator.infrastructure.adapter.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.genailab.orchestrator.domain.model.OrchestrationRequest;
import com.genailab.orchestrator.domain.model.RoutingTarget;
import org.junit.jupiter.api.Test;

class RuleBasedRoutingClassifierTest {

  private final RuleBasedRoutingClassifier classifier = new RuleBasedRoutingClassifier();

  @Test
  void shouldRouteToDiagnosisWhenQuestionContainsStackTrace() {
    RoutingTarget target = classifier.classify(new OrchestrationRequest(
        "java.lang.NullPointerException\n at com.example.Foo.bar(Foo.java:12)"));

    assertThat(target).isEqualTo(RoutingTarget.DIAGNOSIS);
  }

  @Test
  void shouldRouteToDiagnosisBeforeDocQueryWhenErrorMentionsArchitecture() {
    RoutingTarget target = classifier.classify(new OrchestrationRequest(
        "Error en arquitectura: IllegalArgumentException at Foo.bar(Foo.java:12)"));

    assertThat(target).isEqualTo(RoutingTarget.DIAGNOSIS);
  }

  @Test
  void shouldRouteToDocQueryWhenQuestionMentionsDocumentationConcepts() {
    RoutingTarget target = classifier.classify(new OrchestrationRequest(
        "Que son las entidades en la documentacion de arquitectura?"));

    assertThat(target).isEqualTo(RoutingTarget.DOC_QUERY);
  }

  @Test
  void shouldRouteToSmartSearchByDefault() {
    RoutingTarget target = classifier.classify(new OrchestrationRequest("Que dia es hoy?"));

    assertThat(target).isEqualTo(RoutingTarget.SMART_SEARCH);
  }
}
