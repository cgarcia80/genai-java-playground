package com.genailab.orchestrator.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("agents")
public record AppProperties(
    AgentConfig docQueryAgent,
    AgentConfig diagnosisAgent,
    AgentConfig smartSearchAgent,
    Duration connectTimeout,
    Duration readTimeout) {

  public record AgentConfig(String url) {
  }
}
