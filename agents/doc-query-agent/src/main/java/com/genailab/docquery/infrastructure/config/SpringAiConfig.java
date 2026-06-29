package com.genailab.docquery.infrastructure.config;

import com.genailab.docquery.infrastructure.adapter.ingestion.OverlappingTokenTextSplitter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiConfig {

  @Bean
  ChatClient chatClient(
      ChatClient.Builder chatClientBuilder,
      VectorStore vectorStore,
      AppProperties appProperties) {
    SearchRequest searchRequest = SearchRequest.builder()
        .topK(appProperties.rag().topK())
        .similarityThreshold(appProperties.rag().similarityThreshold())
        .build();
    QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore)
        .searchRequest(searchRequest)
        .build();
    return chatClientBuilder.defaultAdvisors(advisor).build();
  }

  @Bean
  TokenTextSplitter tokenTextSplitter(AppProperties appProperties) {
    return new OverlappingTokenTextSplitter(
        appProperties.rag().chunkSize(),
        appProperties.rag().chunkOverlap());
  }
}
