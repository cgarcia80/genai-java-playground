package com.genailab.docquery.infrastructure.adapter.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.genailab.docquery.application.usecase.IngestDocumentsUseCase;
import com.genailab.docquery.application.usecase.QueryDocumentUseCase;
import com.genailab.docquery.domain.model.QueryRequest;
import com.genailab.docquery.domain.model.QueryResponse;
import com.genailab.docquery.domain.port.DocumentIngestionPort;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocQueryController.class)
class DocQueryControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private IngestDocumentsUseCase ingestUseCase;

  @MockitoBean
  private QueryDocumentUseCase queryUseCase;

  @Test
  void shouldReturnBadRequestWhenQuestionIsBlank() throws Exception {
    mockMvc.perform(post("/api/v1/query")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"question\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
  }

  @Test
  void shouldReturnBadRequestWhenQuestionIsMissing() throws Exception {
    mockMvc.perform(post("/api/v1/query")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
  }

  @Test
  void shouldReturnBadRequestWhenBodyIsMissing() throws Exception {
    mockMvc.perform(post("/api/v1/query")
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
  }

  @Test
  void shouldReturnOkWhenIngestSucceeds() throws Exception {
    when(ingestUseCase.execute()).thenReturn(new DocumentIngestionPort.IngestResult(2, 12));

    mockMvc.perform(post("/api/v1/ingest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.files_processed").value(2))
        .andExpect(jsonPath("$.chunks_loaded").value(12));
  }

  @Test
  void shouldReturnOkWhenQueryBodyIsValid() throws Exception {
    when(queryUseCase.execute(new QueryRequest("architecture design recommendations")))
        .thenReturn(new QueryResponse(
            "Use clear module boundaries.",
            List.of(new QueryResponse.SourceRef("architecture.pdf", "Architecture guidance."))));

    mockMvc.perform(post("/api/v1/query")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"question\":\"architecture design recommendations\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer").value("Use clear module boundaries."))
        .andExpect(jsonPath("$.sources[0].file").value("architecture.pdf"));
  }
}
