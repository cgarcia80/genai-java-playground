package com.genailab.docquery.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genailab.docquery.domain.port.DocumentIngestionPort;
import org.junit.jupiter.api.Test;

class IngestDocumentsUseCaseTest {

  @Test
  void shouldDelegateToIngestionPortWhenExecute() {
    DocumentIngestionPort ingestionPort = mock(DocumentIngestionPort.class);
    DocumentIngestionPort.IngestResult expectedResult =
        new DocumentIngestionPort.IngestResult(2, 10);
    when(ingestionPort.ingestAll()).thenReturn(expectedResult);
    IngestDocumentsUseCase useCase = new IngestDocumentsUseCase(ingestionPort);

    DocumentIngestionPort.IngestResult result = useCase.execute();

    assertThat(result).isEqualTo(expectedResult);
    verify(ingestionPort).ingestAll();
  }
}
