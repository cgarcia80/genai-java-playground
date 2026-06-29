package com.genailab.diagnosis.infrastructure.adapter.rest;

public record DiagnosisResponseDto(String rootCause, String location, String suggestion) {}
