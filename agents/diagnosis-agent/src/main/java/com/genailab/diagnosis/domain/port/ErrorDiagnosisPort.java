package com.genailab.diagnosis.domain.port;

import com.genailab.diagnosis.domain.model.DiagnosisRequest;
import com.genailab.diagnosis.domain.model.DiagnosisResponse;

public interface ErrorDiagnosisPort {

  DiagnosisResponse diagnose(DiagnosisRequest request);
}
