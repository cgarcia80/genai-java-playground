package com.genailab.orchestrator.infrastructure.adapter.rest;

public record AskResponseDto(String answer, String routedTo) {
  public AskResponseDto {
    if ("none".equalsIgnoreCase(routedTo)) {
      routedTo = null;
    }
  }
}

