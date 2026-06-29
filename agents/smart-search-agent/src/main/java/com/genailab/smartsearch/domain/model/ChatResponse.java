package com.genailab.smartsearch.domain.model;

import java.util.List;

public record ChatResponse(String answer, List<String> toolsUsed) {}
