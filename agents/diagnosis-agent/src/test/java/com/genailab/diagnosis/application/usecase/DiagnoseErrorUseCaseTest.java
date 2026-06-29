package com.genailab.diagnosis.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.genailab.diagnosis.domain.model.DiagnosisRequest;
import com.genailab.diagnosis.domain.model.DiagnosisResponse;
import com.genailab.diagnosis.domain.port.ErrorDiagnosisPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiagnoseErrorUseCaseTest {

  @Mock
  private ErrorDiagnosisPort port;

  @InjectMocks
  private DiagnoseErrorUseCase useCase;

  @Test
  void shouldDelegateToPort() {
    DiagnosisRequest request = new DiagnosisRequest("java.lang.NullPointerException");
    DiagnosisResponse expected = new DiagnosisResponse("NPE", "SomeClass:42", "Check for null");
    when(port.diagnose(request)).thenReturn(expected);

    DiagnosisResponse result = useCase.diagnose(request);

    assertThat(result).isEqualTo(expected);
  }
}
