package com.genailab.docquery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.genailab.docquery.infrastructure.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class DocQueryAgentApplication {

  public static void main(String[] args) {
    SpringApplication.run(DocQueryAgentApplication.class, args);
  }
}
