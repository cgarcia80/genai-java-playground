package com.genailab.diagnosis.infrastructure.adapter.rest;

import com.genailab.diagnosis.application.usecase.DiagnoseErrorUseCase;
import com.genailab.diagnosis.domain.model.DiagnosisRequest;
import com.genailab.diagnosis.domain.model.DiagnosisResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DiagnosisController {

  private final DiagnoseErrorUseCase useCase;

  public DiagnosisController(DiagnoseErrorUseCase useCase) {
    this.useCase = useCase;
  }

  @PostMapping("/diagnose")
  public ResponseEntity<DiagnosisResponseDto> diagnose(
      @Valid @RequestBody DiagnosisRequestDto request) {
    DiagnosisResponse response = useCase.diagnose(new DiagnosisRequest(request.log()));
    return ResponseEntity.ok(
        new DiagnosisResponseDto(response.rootCause(), response.location(), response.suggestion()));
  }
}
