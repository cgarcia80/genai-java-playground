package com.genailab.orchestrator.infrastructure.adapter.ai;

import com.genailab.orchestrator.domain.exception.DownstreamAgentException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AgentTools {

  private static final ThreadLocal<List<String>> ROUTED_TO =
      ThreadLocal.withInitial(ArrayList::new);

  private final RestClient docQueryClient;
  private final RestClient diagnosisClient;
  private final RestClient smartSearchClient;

  public AgentTools(
      @Qualifier("docQueryClient") RestClient docQueryClient,
      @Qualifier("diagnosisClient") RestClient diagnosisClient,
      @Qualifier("smartSearchClient") RestClient smartSearchClient) {
    this.docQueryClient = docQueryClient;
    this.diagnosisClient = diagnosisClient;
    this.smartSearchClient = smartSearchClient;
  }

  public void clearRoutedTo() {
    ROUTED_TO.get().clear();
  }

  public List<String> getRoutedTo() {
    return Collections.unmodifiableList(ROUTED_TO.get());
  }

  @Tool(description = "Use when the question asks about internal documentation, specific documented features or processes. Forward the full question as the query.")
  public String callDocQueryAgent(String query) {
    ROUTED_TO.get().add("doc-query-agent");
    DocQueryResponse response = post(
        docQueryClient,
        "/api/v1/query",
        new DocQueryRequest(query),
        DocQueryResponse.class,
        "doc-query-agent");
    return requireText(response.answer(), "doc-query-agent");
  }

  @Tool(description = "Use when the user provides a stack trace, error log, or exception message, or asks to diagnose an error. Extract only the relevant log, stack trace or error text from the question and pass it as the logContent parameter. Do not include conversational wrapper text.")
  public String callDiagnosisAgent(String logContent) {
    ROUTED_TO.get().add("diagnosis-agent");
    DiagnosisResponse response = post(
        diagnosisClient,
        "/api/v1/diagnose",
        new DiagnosisRequest(logContent),
        DiagnosisResponse.class,
        "diagnosis-agent");
    return diagnosisAnswer(response);
  }

  @Tool(description = "Use for general questions requiring search, reasoning, or current-date information. Use when the question does not fit documentation lookup or error diagnosis.")
  public String callSmartSearchAgent(String question) {
    ROUTED_TO.get().add("smart-search-agent");
    SmartSearchResponse response = post(
        smartSearchClient,
        "/api/v1/chat",
        new SmartSearchRequest(question),
        SmartSearchResponse.class,
        "smart-search-agent");
    return requireText(response.answer(), "smart-search-agent");
  }

  private <T> T post(
      RestClient client,
      String uri,
      Object request,
      Class<T> responseType,
      String agentName) {
    try {
      T response = client.post()
          .uri(uri)
          .body(request)
          .retrieve()
          .body(responseType);
      if (response == null) {
        throw new DownstreamAgentException(agentName + " returned an empty response");
      }
      return response;
    } catch (RestClientException e) {
      throw new DownstreamAgentException(agentName + " request failed", e);
    }
  }

  private String diagnosisAnswer(DiagnosisResponse response) {
    String rootCause = requireText(response.rootCause(), "diagnosis-agent");
    String location = textOrUnknown(response.location());
    String suggestion = textOrUnknown(response.suggestion());
    return "Root cause: " + rootCause
        + "\nLocation: " + location
        + "\nSuggestion: " + suggestion;
  }

  private String requireText(String value, String agentName) {
    if (value == null || value.isBlank()) {
      throw new DownstreamAgentException(agentName + " returned an empty answer");
    }
    return value;
  }

  private String textOrUnknown(String value) {
    return value == null || value.isBlank() ? "unknown" : value;
  }
}

record DocQueryRequest(String question) {
}

record DocQueryResponse(String answer) {
}

record DiagnosisRequest(String log) {
}

record DiagnosisResponse(String rootCause, String location, String suggestion) {
}

record SmartSearchRequest(String question) {
}

record SmartSearchResponse(String answer) {
}
