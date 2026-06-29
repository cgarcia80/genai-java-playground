package com.genailab.smartsearch.infrastructure.adapter.rest;

import com.genailab.smartsearch.application.usecase.ChatUseCase;
import com.genailab.smartsearch.domain.model.ChatRequest;
import com.genailab.smartsearch.domain.model.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

  private final ChatUseCase useCase;

  public ChatController(ChatUseCase useCase) {
    this.useCase = useCase;
  }

  @PostMapping("/chat")
  public ResponseEntity<ChatResponseDto> chat(@Valid @RequestBody ChatRequestDto request) {
    ChatResponse response = useCase.chat(new ChatRequest(request.question()));
    return ResponseEntity.ok(new ChatResponseDto(response.answer(), response.toolsUsed()));
  }
}
