package com.genailab.smartsearch.application.usecase;

import com.genailab.smartsearch.domain.model.ChatRequest;
import com.genailab.smartsearch.domain.model.ChatResponse;
import com.genailab.smartsearch.domain.port.ChatPort;
import org.springframework.stereotype.Service;

@Service
public class ChatUseCase {

  private final ChatPort port;

  public ChatUseCase(ChatPort port) {
    this.port = port;
  }

  public ChatResponse chat(ChatRequest request) {
    return port.chat(request);
  }
}
