package com.genailab.docquery.infrastructure.adapter.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class QueryRequestDto {

  @JsonProperty("question")
  @NotBlank(message = "El campo 'question' es requerido y no puede estar vacío.")
  private String question;

  public QueryRequestDto() {
  }

  public QueryRequestDto(String question) {
    this.question = question;
  }

  public String question() {
    return question;
  }

  public void setQuestion(String question) {
    this.question = question;
  }
}
