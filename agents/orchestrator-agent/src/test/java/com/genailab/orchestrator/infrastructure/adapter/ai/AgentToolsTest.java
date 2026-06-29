package com.genailab.orchestrator.infrastructure.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.genailab.orchestrator.domain.exception.DownstreamAgentException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AgentToolsTest {

  @Test
  void shouldCallDocQueryAgentAndTrackRoute() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://doc-query-agent:8080");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    AgentTools tools = new AgentTools(builder.build(), unusedClient(), unusedClient());
    tools.clearRoutedTo();
    server.expect(once(), requestTo("http://doc-query-agent:8080/api/v1/query"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("{\"question\":\"Que son las entidades?\"}"))
        .andRespond(withSuccess("{\"answer\":\"Una entidad tiene identidad.\"}", APPLICATION_JSON));

    String answer = tools.callDocQueryAgent("Que son las entidades?");

    assertThat(answer).isEqualTo("Una entidad tiene identidad.");
    assertThat(tools.getRoutedTo()).containsExactly("doc-query-agent");
    server.verify();
  }

  @Test
  void shouldCallDiagnosisAgentWithLogPayloadAndFlattenResponse() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://diagnosis-agent:8081");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    AgentTools tools = new AgentTools(unusedClient(), builder.build(), unusedClient());
    tools.clearRoutedTo();
    server.expect(once(), requestTo("http://diagnosis-agent:8081/api/v1/diagnose"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("{\"log\":\"NullPointerException at Foo.bar\"}"))
        .andRespond(withSuccess("""
            {
              "rootCause": "Null value",
              "location": "Foo.bar:12",
              "suggestion": "Validate the input"
            }
            """, APPLICATION_JSON));

    String answer = tools.callDiagnosisAgent("NullPointerException at Foo.bar");

    assertThat(answer).contains("Root cause: Null value");
    assertThat(answer).contains("Location: Foo.bar:12");
    assertThat(answer).contains("Suggestion: Validate the input");
    assertThat(tools.getRoutedTo()).containsExactly("diagnosis-agent");
    server.verify();
  }

  @Test
  void shouldCallSmartSearchAgentAndTrackRoute() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://smart-search-agent:8082");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    AgentTools tools = new AgentTools(unusedClient(), unusedClient(), builder.build());
    tools.clearRoutedTo();
    server.expect(once(), requestTo("http://smart-search-agent:8082/api/v1/chat"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("{\"question\":\"Que dia es hoy?\"}"))
        .andRespond(withSuccess("{\"answer\":\"Hoy es 2026-06-29.\"}", APPLICATION_JSON));

    String answer = tools.callSmartSearchAgent("Que dia es hoy?");

    assertThat(answer).isEqualTo("Hoy es 2026-06-29.");
    assertThat(tools.getRoutedTo()).containsExactly("smart-search-agent");
    server.verify();
  }

  @Test
  void shouldThrowDownstreamExceptionWhenAgentReturnsError() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://doc-query-agent:8080");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    AgentTools tools = new AgentTools(builder.build(), unusedClient(), unusedClient());
    tools.clearRoutedTo();
    server.expect(once(), requestTo("http://doc-query-agent:8080/api/v1/query"))
        .andRespond(withServerError());

    assertThatThrownBy(() -> tools.callDocQueryAgent("Que son las entidades?"))
        .isInstanceOf(DownstreamAgentException.class)
        .hasMessage("doc-query-agent request failed");
    server.verify();
  }

  private RestClient unusedClient() {
    return RestClient.builder().baseUrl("http://unused").build();
  }
}
