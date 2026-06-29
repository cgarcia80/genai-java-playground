package com.genailab.docquery.domain.port;

public interface DocumentIngestionPort {

  IngestResult ingestAll();

  record IngestResult(int filesProcessed, int chunksLoaded) {
  }
}
