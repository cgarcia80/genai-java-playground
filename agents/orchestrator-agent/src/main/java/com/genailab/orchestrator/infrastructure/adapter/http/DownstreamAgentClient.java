package com.genailab.orchestrator.infrastructure.adapter.http;

import com.genailab.orchestrator.domain.exception.DownstreamAgentException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class DownstreamAgentClient {

  private final RestClient docQueryClient;
  private final RestClient diagnosisClient;
  private final RestClient smartSearchClient;

  public DownstreamAgentClient(
      @Qualifier("docQueryClient") RestClient docQueryClient,
      @Qualifier("diagnosisClient") RestClient diagnosisClient,
      @Qualifier("smartSearchClient") RestClient smartSearchClient) {
    this.docQueryClient = docQueryClient;
    this.diagnosisClient = diagnosisClient;
    this.smartSearchClient = smartSearchClient;
  }

  public String callDocQueryAgent(String question) {
    DocQueryResponse response = post(
        docQueryClient,
        "/api/v1/query",
        new DocQueryRequest(question),
        DocQueryResponse.class,
        "doc-query-agent");
    return requireText(response.answer(), "doc-query-agent");
  }

  public String callDiagnosisAgent(String logContent) {
    DiagnosisResponse response = post(
        diagnosisClient,
        "/api/v1/diagnose",
        new DiagnosisRequest(logContent),
        DiagnosisResponse.class,
        "diagnosis-agent");
    return diagnosisAnswer(response);
  }

  public String callSmartSearchAgent(String question) {
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
