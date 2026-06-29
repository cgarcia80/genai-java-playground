package com.genailab.orchestrator.infrastructure.adapter.rest;

import com.genailab.orchestrator.domain.exception.DownstreamAgentException;
import com.genailab.orchestrator.domain.exception.InvalidRoutingTargetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final String BAD_REQUEST = "BAD_REQUEST";
  private static final String BAD_GATEWAY = "BAD_GATEWAY";
  private static final String INTERNAL_ERROR = "INTERNAL_ERROR";
  private static final String VALIDATION_MESSAGE =
      "El campo 'question' es requerido y no puede estar vacio.";
  private static final String INVALID_ROUTING_MESSAGE =
      "El header X-Bypass-Routing debe ser doc-query, diagnosis o smart-search.";
  private static final String DOWNSTREAM_MESSAGE =
      "El agente downstream no respondio correctamente.";
  private static final String UNEXPECTED_MESSAGE = "Ocurrio un error inesperado.";

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
    log.warn("Validation failed for request body: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(BAD_REQUEST, VALIDATION_MESSAGE));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadableMessage(
      HttpMessageNotReadableException exception) {
    log.warn("Request body could not be read: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(BAD_REQUEST, VALIDATION_MESSAGE));
  }

  @ExceptionHandler(InvalidRoutingTargetException.class)
  public ResponseEntity<ErrorResponse> handleInvalidRoutingTarget() {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(BAD_REQUEST, INVALID_ROUTING_MESSAGE));
  }

  @ExceptionHandler(DownstreamAgentException.class)
  public ResponseEntity<ErrorResponse> handleDownstream(DownstreamAgentException exception) {
    log.error("Downstream agent failed: {}", exception.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(new ErrorResponse(BAD_GATEWAY, DOWNSTREAM_MESSAGE));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
    log.error("Unexpected orchestrator error", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse(INTERNAL_ERROR, UNEXPECTED_MESSAGE));
  }
}
