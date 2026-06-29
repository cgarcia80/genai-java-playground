package com.genailab.orchestrator.domain.exception;

public class InvalidRoutingTargetException extends RuntimeException {

  public InvalidRoutingTargetException(String value) {
    super("Invalid routing target: " + value);
  }
}
