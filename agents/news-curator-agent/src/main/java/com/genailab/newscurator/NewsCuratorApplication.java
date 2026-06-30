package com.genailab.newscurator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NewsCuratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(NewsCuratorApplication.class, args);
    }
}
