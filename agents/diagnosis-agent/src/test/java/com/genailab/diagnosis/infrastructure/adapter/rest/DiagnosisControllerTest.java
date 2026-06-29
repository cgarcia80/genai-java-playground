package com.genailab.diagnosis.infrastructure.adapter.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.genailab.diagnosis.application.usecase.DiagnoseErrorUseCase;
import com.genailab.diagnosis.domain.model.DiagnosisResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DiagnosisController.class)
class DiagnosisControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private DiagnoseErrorUseCase useCase;

  @Test
  void shouldReturnBadRequestWhenLogIsBlank() throws Exception {
    mockMvc.perform(post("/api/v1/diagnose")
            .contentType("application/json")
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
  }

  @Test
  void shouldReturnDiagnosisWhenLogIsValid() throws Exception {
    when(useCase.diagnose(any())).thenReturn(
        new DiagnosisResponse("NullPointerException in UserService", "UserService.java:42",
            "Check for null before calling findById"));

    mockMvc.perform(post("/api/v1/diagnose")
            .contentType("application/json")
            .content("{\"log\":\"java.lang.NullPointerException at UserService.java:42\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rootCause").value("NullPointerException in UserService"))
        .andExpect(jsonPath("$.location").value("UserService.java:42"))
        .andExpect(jsonPath("$.suggestion").exists());
  }
}
