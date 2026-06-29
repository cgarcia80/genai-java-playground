package com.genailab.diagnosis.infrastructure.adapter.rest;

import jakarta.validation.constraints.NotBlank;

public record DiagnosisRequestDto(
    @NotBlank(message = "El campo 'log' es requerido y no puede estar vacío.") String log) {}
