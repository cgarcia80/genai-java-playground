package com.genailab.orchestrator.domain.model;

import com.genailab.orchestrator.domain.exception.InvalidRoutingTargetException;
import java.util.Arrays;

public enum RoutingTarget {
  DOC_QUERY("doc-query", "doc-query-agent"),
  DIAGNOSIS("diagnosis", "diagnosis-agent"),
  SMART_SEARCH("smart-search", "smart-search-agent");

  private final String headerValue;
  private final String agentName;

  RoutingTarget(String headerValue, String agentName) {
    this.headerValue = headerValue;
    this.agentName = agentName;
  }

  public String agentName() {
    return agentName;
  }

  public static RoutingTarget fromHeader(String value) {
    return Arrays.stream(values())
        .filter(target -> target.headerValue.equals(value))
        .findFirst()
        .orElseThrow(() -> new InvalidRoutingTargetException(value));
  }
}
