package com.genailab.smartsearch.infrastructure.adapter.ai;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

@Component
public class SearchTools {

  private static final int TOP_K = 3;

  private final VectorStore vectorStore;
  // ThreadLocal porque Spring AI invoca las tools en el mismo hilo de la llamada HTTP
  private static final ThreadLocal<List<String>> TOOLS_USED =
      ThreadLocal.withInitial(ArrayList::new);

  public SearchTools(VectorStore vectorStore) {
    this.vectorStore = vectorStore;
  }

  public void clearToolsUsed() {
    TOOLS_USED.get().clear();
  }

  @Tool(description = "Search internal documentation to answer questions about the project, architecture, entities, or any content in the loaded documents.")
  public String searchDocs(String query) {
    TOOLS_USED.get().add("searchDocs");
    return vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(TOP_K).build())
        .stream()
        .map(doc -> doc.getText())
        .collect(Collectors.joining("\n---\n"));
  }

  @Tool(description = "Get the current date in ISO-8601 format.")
  public String getCurrentDate() {
    TOOLS_USED.get().add("getCurrentDate");
    return LocalDate.now().toString();
  }

  public List<String> getToolsUsed() {
    return Collections.unmodifiableList(TOOLS_USED.get());
  }
}
