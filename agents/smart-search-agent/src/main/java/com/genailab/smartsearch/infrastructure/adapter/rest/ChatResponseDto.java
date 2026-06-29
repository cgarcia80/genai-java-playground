package com.genailab.smartsearch.infrastructure.adapter.rest;

import java.util.List;

public record ChatResponseDto(String answer, List<String> toolsUsed) {}
