package com.genailab.orchestrator.infrastructure.adapter.rest;

import jakarta.validation.constraints.NotBlank;

public record AskRequestDto(
    @NotBlank(message = "El campo 'question' es requerido y no puede estar vacio.") String question) {
}
