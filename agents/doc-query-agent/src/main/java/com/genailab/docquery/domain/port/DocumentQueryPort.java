package com.genailab.docquery.domain.port;

import com.genailab.docquery.domain.model.QueryRequest;
import com.genailab.docquery.domain.model.QueryResponse;

public interface DocumentQueryPort {

  QueryResponse query(QueryRequest request);
}
