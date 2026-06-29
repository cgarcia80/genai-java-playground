package com.genailab.docquery.infrastructure.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genailab.docquery.domain.model.QueryRequest;
import com.genailab.docquery.domain.model.QueryResponse;
import com.genailab.docquery.infrastructure.config.AppProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

class SpringAiDocumentQueryAdapterTest {

  @Test
  void shouldPassQuestionAndMapResponseWhenQuery() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
    VectorStore vectorStore = mock(VectorStore.class);
    Document document = Document.builder()
        .text("The payment flow starts when the user confirms the operation.")
        .metadata("file_name", "payments.md")
        .score(0.91)
        .build();
    ChatResponse chatResponse = new ChatResponse(
        List.of(new Generation(new AssistantMessage("The payment flow starts on confirmation."))));
    ChatClientResponse clientResponse = new ChatClientResponse(
        chatResponse,
        Map.of(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS, List.of(document)));
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.user("How does the payment flow start?")).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(responseSpec);
    when(responseSpec.chatClientResponse()).thenReturn(clientResponse);
    SpringAiDocumentQueryAdapter adapter =
        new SpringAiDocumentQueryAdapter(chatClient, vectorStore, properties());

    QueryResponse response = adapter.query(new QueryRequest("How does the payment flow start?"));

    assertThat(response.answer()).isEqualTo("The payment flow starts on confirmation.");
    assertThat(response.sources())
        .containsExactly(new QueryResponse.SourceRef(
            "payments.md",
            "The payment flow starts when the user confirms the operation."));
    verify(requestSpec).user("How does the payment flow start?");
  }

  @Test
  void shouldReturnStandardAnswerWhenNoSourcesAreRetrieved() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
    VectorStore vectorStore = mock(VectorStore.class);
    ChatClientResponse clientResponse = new ChatClientResponse(
        new ChatResponse(List.of(new Generation(new AssistantMessage("Generic answer.")))),
        Map.of(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS, List.of()));
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.user("How many moons does Jupiter have?")).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(responseSpec);
    when(responseSpec.chatClientResponse()).thenReturn(clientResponse);
    SpringAiDocumentQueryAdapter adapter =
        new SpringAiDocumentQueryAdapter(chatClient, vectorStore, properties());

    QueryResponse response = adapter.query(new QueryRequest("How many moons does Jupiter have?"));

    assertThat(response.answer()).isEqualTo(SpringAiDocumentQueryAdapter.NO_RELEVANT_INFORMATION);
    assertThat(response.sources()).isEmpty();
  }

  @Test
  void shouldReturnStandardAnswerWhenRetrievedSourceScoreIsLow() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
    VectorStore vectorStore = mock(VectorStore.class);
    Document document = Document.builder()
        .text("A weakly related payment fragment.")
        .metadata("file_name", "payments.md")
        .score(0.25)
        .build();
    ChatClientResponse clientResponse = new ChatClientResponse(
        new ChatResponse(List.of(new Generation(new AssistantMessage("Generic answer.")))),
        Map.of(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS, List.of(document)));
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.user("How many moons does Jupiter have?")).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(responseSpec);
    when(responseSpec.chatClientResponse()).thenReturn(clientResponse);
    SpringAiDocumentQueryAdapter adapter =
        new SpringAiDocumentQueryAdapter(chatClient, vectorStore, properties());

    QueryResponse response = adapter.query(new QueryRequest("How many moons does Jupiter have?"));

    assertThat(response.answer()).isEqualTo(SpringAiDocumentQueryAdapter.NO_RELEVANT_INFORMATION);
    assertThat(response.sources()).isEmpty();
  }

  private AppProperties properties() {
    return new AppProperties("/app/docs", new AppProperties.Rag(5, 512, 50, 0.70));
  }
}
