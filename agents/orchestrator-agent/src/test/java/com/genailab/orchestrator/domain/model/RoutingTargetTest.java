package com.genailab.orchestrator.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.genailab.orchestrator.domain.exception.InvalidRoutingTargetException;
import org.junit.jupiter.api.Test;

class RoutingTargetTest {

  @Test
  void shouldParseValidHeaderValues() {
    assertThat(RoutingTarget.fromHeader("doc-query")).isEqualTo(RoutingTarget.DOC_QUERY);
    assertThat(RoutingTarget.fromHeader("diagnosis")).isEqualTo(RoutingTarget.DIAGNOSIS);
    assertThat(RoutingTarget.fromHeader("smart-search")).isEqualTo(RoutingTarget.SMART_SEARCH);
  }

  @Test
  void shouldExposeAgentNames() {
    assertThat(RoutingTarget.DOC_QUERY.agentName()).isEqualTo("doc-query-agent");
    assertThat(RoutingTarget.DIAGNOSIS.agentName()).isEqualTo("diagnosis-agent");
    assertThat(RoutingTarget.SMART_SEARCH.agentName()).isEqualTo("smart-search-agent");
  }

  @Test
  void shouldRejectInvalidHeaderValue() {
    assertThatThrownBy(() -> RoutingTarget.fromHeader("unknown"))
        .isInstanceOf(InvalidRoutingTargetException.class)
        .hasMessage("Invalid routing target: unknown");
  }
}
