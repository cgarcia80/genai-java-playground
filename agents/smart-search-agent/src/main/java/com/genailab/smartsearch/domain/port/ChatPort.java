package com.genailab.smartsearch.domain.port;

import com.genailab.smartsearch.domain.model.ChatRequest;
import com.genailab.smartsearch.domain.model.ChatResponse;

public interface ChatPort {

  ChatResponse chat(ChatRequest request);
}
