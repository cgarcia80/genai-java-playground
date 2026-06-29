package com.genailab.docquery.infrastructure.adapter.rest;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final String BAD_REQUEST = "BAD_REQUEST";
  private static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
  private static final String INTERNAL_ERROR = "INTERNAL_ERROR";
  private static final String VALIDATION_MESSAGE =
      "El campo 'question' es requerido y no puede estar vacío.";
  private static final String CONNECTIVITY_MESSAGE =
      "No se pudo conectar con el servicio de embeddings o vector store.";
  private static final String UNEXPECTED_MESSAGE = "Ocurrió un error inesperado.";

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
    LOGGER.warn("Validation failed for request body: {}", exception.getMessage());
    return badRequest();
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException exception) {
    LOGGER.warn("Request body could not be read: {}", exception.getMessage());
    return badRequest();
  }

  private ResponseEntity<ErrorResponse> badRequest() {
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(BAD_REQUEST, VALIDATION_MESSAGE));
  }

  @ExceptionHandler({ResourceAccessException.class, ConnectException.class, TimeoutException.class})
  public ResponseEntity<ErrorResponse> handleConnectivity() {
    return ResponseEntity
        .status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ErrorResponse(SERVICE_UNAVAILABLE, CONNECTIVITY_MESSAGE));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected() {
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse(INTERNAL_ERROR, UNEXPECTED_MESSAGE));
  }
}
