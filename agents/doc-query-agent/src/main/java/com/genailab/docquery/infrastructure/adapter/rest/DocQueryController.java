package com.genailab.docquery.infrastructure.adapter.rest;

import com.genailab.docquery.application.usecase.IngestDocumentsUseCase;
import com.genailab.docquery.application.usecase.QueryDocumentUseCase;
import com.genailab.docquery.domain.model.QueryRequest;
import com.genailab.docquery.domain.model.QueryResponse;
import com.genailab.docquery.domain.port.DocumentIngestionPort;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DocQueryController {

  private final IngestDocumentsUseCase ingestUseCase;
  private final QueryDocumentUseCase queryUseCase;

  public DocQueryController(
      IngestDocumentsUseCase ingestUseCase,
      QueryDocumentUseCase queryUseCase) {
    this.ingestUseCase = ingestUseCase;
    this.queryUseCase = queryUseCase;
  }

  @PostMapping("/ingest")
  public ResponseEntity<IngestResponse> ingest() {
    DocumentIngestionPort.IngestResult result = ingestUseCase.execute();
    return ResponseEntity.ok(new IngestResponse(result.filesProcessed(), result.chunksLoaded()));
  }

  @PostMapping("/query")
  public ResponseEntity<QueryResponseDto> query(@Valid @RequestBody QueryRequestDto request) {
    QueryResponse response = queryUseCase.execute(new QueryRequest(request.question()));
    return ResponseEntity.ok(toDto(response));
  }

  private QueryResponseDto toDto(QueryResponse response) {
    List<SourceRefDto> sources = response.sources().stream()
        .map(source -> new SourceRefDto(source.file(), source.snippet()))
        .toList();
    return new QueryResponseDto(response.answer(), sources);
  }
}
