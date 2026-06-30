package com.genailab.newscurator.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.hacker-news")
public record HackerNewsProperties(
    @DefaultValue("https://hn.algolia.com/api/v1") String algoliaBaseUrl,
    @DefaultValue("https://hacker-news.firebaseio.com/v0") String firebaseBaseUrl,
    @DefaultValue("5000") int connectTimeoutMs,
    @DefaultValue("10000") int readTimeoutMs
) {}
