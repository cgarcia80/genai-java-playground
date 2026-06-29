package com.genailab.diagnosis.infrastructure.adapter.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genailab.diagnosis.domain.model.DiagnosisRequest;
import com.genailab.diagnosis.domain.model.DiagnosisResponse;
import com.genailab.diagnosis.domain.port.ErrorDiagnosisPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class SpringAiDiagnosisAdapter implements ErrorDiagnosisPort {

  private static final Logger log = LoggerFactory.getLogger(SpringAiDiagnosisAdapter.class);

  private static final String SYSTEM_PROMPT = """
      You are an expert Java/Spring Boot developer diagnosing errors from logs and stack traces.
      Analyze the error and respond ONLY with a valid JSON object in this exact format, with no other text before or after:
      {
        "rootCause": "brief description of the root cause",
        "location": "ClassName.methodName:lineNumber or 'unknown' if not determinable",
        "suggestion": "concrete actionable suggestion to fix the issue"
      }
      If the input is not an error, still respond with the same JSON format.
      """;

  private final ChatClient chatClient;
  private final ObjectMapper objectMapper;

  public SpringAiDiagnosisAdapter(ChatClient chatClient, ObjectMapper objectMapper) {
    this.chatClient = chatClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public DiagnosisResponse diagnose(DiagnosisRequest request) {
    String content = chatClient.prompt()
        .system(SYSTEM_PROMPT)
        .user(request.log())
        .call()
        .content();
    return parse(content);
  }

  private DiagnosisResponse parse(String content) {
    try {
      String json = extractJson(content);
      JsonNode node = objectMapper.readTree(json);
      return new DiagnosisResponse(
          text(node, "rootCause"),
          text(node, "location"),
          text(node, "suggestion")
      );
    } catch (Exception e) {
      log.warn("Could not parse structured response, returning raw content: {}", e.getMessage());
      return new DiagnosisResponse(content.trim(), "unknown", "Review the analysis above.");
    }
  }

  private String extractJson(String content) {
    int start = content.indexOf('{');
    int end = content.lastIndexOf('}');
    if (start == -1 || end == -1 || start > end) {
      throw new IllegalArgumentException("No JSON object found in response");
    }
    return content.substring(start, end + 1);
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? "unknown" : value.asText();
  }
}
