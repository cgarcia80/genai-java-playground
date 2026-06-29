package com.genailab.docquery.application.usecase;

import com.genailab.docquery.domain.model.QueryRequest;
import com.genailab.docquery.domain.model.QueryResponse;
import com.genailab.docquery.domain.port.DocumentQueryPort;
import org.springframework.stereotype.Service;

@Service
public class QueryDocumentUseCase {

  private final DocumentQueryPort queryPort;

  public QueryDocumentUseCase(DocumentQueryPort queryPort) {
    this.queryPort = queryPort;
  }

  public QueryResponse execute(QueryRequest request) {
    return queryPort.query(request);
  }
}
