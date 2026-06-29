package com.genailab.docquery.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(String docsPath, Rag rag) {

  public record Rag(int topK, int chunkSize, int chunkOverlap, double similarityThreshold) {
  }
}
