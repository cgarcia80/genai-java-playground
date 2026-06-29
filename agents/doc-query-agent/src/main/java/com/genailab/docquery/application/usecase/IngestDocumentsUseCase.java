package com.genailab.docquery.application.usecase;

import com.genailab.docquery.domain.port.DocumentIngestionPort;
import org.springframework.stereotype.Service;

@Service
public class IngestDocumentsUseCase {

  private final DocumentIngestionPort ingestionPort;

  public IngestDocumentsUseCase(DocumentIngestionPort ingestionPort) {
    this.ingestionPort = ingestionPort;
  }

  public DocumentIngestionPort.IngestResult execute() {
    return ingestionPort.ingestAll();
  }
}
