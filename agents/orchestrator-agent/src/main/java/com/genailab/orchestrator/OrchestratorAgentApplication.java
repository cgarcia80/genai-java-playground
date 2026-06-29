package com.genailab.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrchestratorAgentApplication {

  public static void main(String[] args) {
    SpringApplication.run(OrchestratorAgentApplication.class, args);
  }
}
