package com.genailab.docquery.domain.model;

import java.util.List;

public record QueryResponse(String answer, List<SourceRef> sources) {

  public record SourceRef(String file, String snippet) {
  }
}
