package com.genailab.docquery.infrastructure.adapter.rest;

import java.util.List;

public record QueryResponseDto(String answer, List<SourceRefDto> sources) {
}
