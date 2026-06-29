package com.genailab.smartsearch.infrastructure.adapter.ai;

import com.genailab.smartsearch.domain.model.ChatRequest;
import com.genailab.smartsearch.domain.model.ChatResponse;
import com.genailab.smartsearch.domain.port.ChatPort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class SpringAiChatAdapter implements ChatPort {

  private final ChatClient chatClient;
  private final SearchTools searchTools;

  public SpringAiChatAdapter(ChatClient chatClient, SearchTools searchTools) {
    this.chatClient = chatClient;
    this.searchTools = searchTools;
  }

  @Override
  public ChatResponse chat(ChatRequest request) {
    searchTools.clearToolsUsed();
    String answer = chatClient.prompt()
        .user(request.question())
        .tools(searchTools)
        .call()
        .content();
    return new ChatResponse(answer, searchTools.getToolsUsed());
  }
}
