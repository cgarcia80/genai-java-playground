package com.genailab.docquery.infrastructure.adapter.ai;

import com.genailab.docquery.domain.model.QueryRequest;
import com.genailab.docquery.domain.model.QueryResponse;
import com.genailab.docquery.domain.port.DocumentQueryPort;
import com.genailab.docquery.infrastructure.config.AppProperties;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

@Component
public class SpringAiDocumentQueryAdapter implements DocumentQueryPort {

  private static final Logger log = LoggerFactory.getLogger(SpringAiDocumentQueryAdapter.class);

  public static final String NO_RELEVANT_INFORMATION =
      "No encontré información relevante en la documentación cargada.";
  private static final int SNIPPET_LENGTH = 200;
  private static final String FILE_NAME_METADATA_KEY = "file_name";
  private static final String UNKNOWN_FILE = "unknown";

  private final ChatClient chatClient;
  private final VectorStore vectorStore;
  private final AppProperties properties;

  public SpringAiDocumentQueryAdapter(
      ChatClient chatClient,
      VectorStore vectorStore,
      AppProperties properties) {
    this.chatClient = chatClient;
    this.vectorStore = vectorStore;
    this.properties = properties;
  }

  @Override
  public QueryResponse query(QueryRequest request) {
    ChatClientResponse response = chatClient.prompt()
        .user(request.question())
        .call()
        .chatClientResponse();
    List<Document> documents = relevantDocuments(response.context());
    if (documents.isEmpty()) {
      return new QueryResponse(NO_RELEVANT_INFORMATION, List.of());
    }
    return new QueryResponse(answer(response.chatResponse()), sources(documents));
  }

  private List<Document> relevantDocuments(Map<String, Object> context) {
    Object documents = context.get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
    if (!(documents instanceof List<?> values)) {
      return List.of();
    }
    return values.stream()
        .filter(Document.class::isInstance)
        .map(Document.class::cast)
        .filter(this::hasRelevantScore)
        .toList();
  }

  private boolean hasRelevantScore(Document document) {
    Double score = document.getScore();
    log.debug("RAG chunk score={} file={}", score, document.getMetadata().get(FILE_NAME_METADATA_KEY));
    return score == null || score >= properties.rag().similarityThreshold();
  }

  private String answer(ChatResponse chatResponse) {
    if (chatResponse == null || chatResponse.getResult() == null) {
      return NO_RELEVANT_INFORMATION;
    }
    String text = chatResponse.getResult().getOutput().getText();
    return text == null || text.isBlank() ? NO_RELEVANT_INFORMATION : text;
  }

  private List<QueryResponse.SourceRef> sources(List<Document> documents) {
    return documents.stream()
        .map(document -> new QueryResponse.SourceRef(sourceFile(document), snippet(document)))
        .toList();
  }

  private String sourceFile(Document document) {
    Object fileName = document.getMetadata().get(FILE_NAME_METADATA_KEY);
    return fileName == null ? UNKNOWN_FILE : fileName.toString();
  }

  private String snippet(Document document) {
    String text = document.getText() == null ? "" : document.getText().replaceAll("\\s+", " ").trim();
    return text.length() <= SNIPPET_LENGTH ? text : text.substring(0, SNIPPET_LENGTH);
  }
}
