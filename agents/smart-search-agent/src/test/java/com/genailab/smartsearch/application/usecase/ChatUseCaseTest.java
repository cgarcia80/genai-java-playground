package com.genailab.smartsearch.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.genailab.smartsearch.domain.model.ChatRequest;
import com.genailab.smartsearch.domain.model.ChatResponse;
import com.genailab.smartsearch.domain.port.ChatPort;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatUseCaseTest {

  @Mock
  private ChatPort port;

  @InjectMocks
  private ChatUseCase useCase;

  @Test
  void shouldDelegateToPort() {
    ChatRequest request = new ChatRequest("¿Qué son las entidades?");
    ChatResponse expected = new ChatResponse("Las entidades son...", List.of("searchDocs"));
    when(port.chat(request)).thenReturn(expected);

    ChatResponse result = useCase.chat(request);

    assertThat(result).isEqualTo(expected);
  }
}
