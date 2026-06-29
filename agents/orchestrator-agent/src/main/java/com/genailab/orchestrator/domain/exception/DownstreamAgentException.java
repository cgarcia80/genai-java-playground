package com.genailab.orchestrator.domain.exception;

public class DownstreamAgentException extends RuntimeException {

  public DownstreamAgentException(String message) {
    super(message);
  }

  public DownstreamAgentException(String message, Throwable cause) {
    super(message, cause);
  }
}
