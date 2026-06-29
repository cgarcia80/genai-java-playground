package com.genailab.smartsearch.infrastructure.adapter.rest;

import jakarta.validation.constraints.NotBlank;

public record ChatRequestDto(
    @NotBlank(message = "El campo 'question' es requerido y no puede estar vacío.") String question) {}
