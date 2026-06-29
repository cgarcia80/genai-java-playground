package com.genailab.diagnosis.application.usecase;

import com.genailab.diagnosis.domain.model.DiagnosisRequest;
import com.genailab.diagnosis.domain.model.DiagnosisResponse;
import com.genailab.diagnosis.domain.port.ErrorDiagnosisPort;
import org.springframework.stereotype.Service;

@Service
public class DiagnoseErrorUseCase {

  private final ErrorDiagnosisPort port;

  public DiagnoseErrorUseCase(ErrorDiagnosisPort port) {
    this.port = port;
  }

  public DiagnosisResponse diagnose(DiagnosisRequest request) {
    return port.diagnose(request);
  }
}
