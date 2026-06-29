package com.genailab.docquery.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genailab.docquery.domain.model.QueryRequest;
import com.genailab.docquery.domain.model.QueryResponse;
import com.genailab.docquery.domain.port.DocumentQueryPort;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryDocumentUseCaseTest {

  @Test
  void shouldDelegateToQueryPortWithSameRequestWhenExecute() {
    DocumentQueryPort queryPort = mock(DocumentQueryPort.class);
    QueryRequest request = new QueryRequest("What does this document say?");
    QueryResponse expectedResponse = new QueryResponse("It explains the flow.", List.of());
    when(queryPort.query(request)).thenReturn(expectedResponse);
    QueryDocumentUseCase useCase = new QueryDocumentUseCase(queryPort);

    QueryResponse response = useCase.execute(request);

    assertThat(response).isEqualTo(expectedResponse);
    verify(queryPort).query(request);
  }
}
