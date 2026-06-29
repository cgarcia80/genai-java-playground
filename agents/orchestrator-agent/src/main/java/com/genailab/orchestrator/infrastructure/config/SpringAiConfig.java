package com.genailab.orchestrator.infrastructure.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class SpringAiConfig {

  @Bean
  @Qualifier("docQueryClient")
  RestClient docQueryClient(RestClient.Builder builder, AppProperties properties) {
    return restClient(builder, properties.docQueryAgent().url(), properties);
  }

  @Bean
  @Qualifier("diagnosisClient")
  RestClient diagnosisClient(RestClient.Builder builder, AppProperties properties) {
    return restClient(builder, properties.diagnosisAgent().url(), properties);
  }

  @Bean
  @Qualifier("smartSearchClient")
  RestClient smartSearchClient(RestClient.Builder builder, AppProperties properties) {
    return restClient(builder, properties.smartSearchAgent().url(), properties);
  }

  private RestClient restClient(
      RestClient.Builder builder,
      String baseUrl,
      AppProperties properties) {
    return builder
        .baseUrl(baseUrl)
        .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout()))
        .build();
  }

  private SimpleClientHttpRequestFactory requestFactory(
      Duration connectTimeout,
      Duration readTimeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    return factory;
  }
}
