package com.genailab.newscurator.infrastructure.adapter.hn;

import com.genailab.newscurator.domain.model.NewsStory;
import com.genailab.newscurator.domain.port.NewsFetcherPort;
import com.genailab.newscurator.infrastructure.config.HackerNewsProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
public class HackerNewsRestAdapter implements NewsFetcherPort {
    private final RestClient algoliaClient;
    private final RestClient firebaseClient;
    private final Executor executor;

    public HackerNewsRestAdapter(
            HackerNewsProperties props,
            @Qualifier("hnTaskExecutor") Executor executor) {
        
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeoutMs());
        factory.setReadTimeout(props.readTimeoutMs());

        this.algoliaClient = RestClient.builder()
                .baseUrl(props.algoliaBaseUrl())
                .requestFactory(factory)
                .build();

        this.firebaseClient = RestClient.builder()
                .baseUrl(props.firebaseBaseUrl())
                .requestFactory(factory)
                .build();
                
        this.executor = executor;
    }

    // Constructor for testing
    HackerNewsRestAdapter(RestClient algoliaClient, RestClient firebaseClient, Executor executor) {
        this.algoliaClient = algoliaClient;
        this.firebaseClient = firebaseClient;
        this.executor = executor;
    }

    @Override
    public List<NewsStory> fetchStories(String topic, int limit) {
        // Query Algolia for matching story IDs
        AlgoliaSearchResponse response = algoliaClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("query", topic)
                        .queryParam("tags", "story")
                        .queryParam("hitsPerPage", limit)
                        .build())
                .retrieve()
                .body(AlgoliaSearchResponse.class);

        if (response == null || response.hits() == null) {
            return List.of();
        }

        // Fetch each story's details concurrently
        List<CompletableFuture<NewsStory>> futures = response.hits().stream()
                .map(hit -> CompletableFuture.supplyAsync(() -> fetchStoryDetail(hit.objectID()), executor))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }

    private NewsStory fetchStoryDetail(String id) {
        try {
            FirebaseStoryItem item = firebaseClient.get()
                    .uri("/item/{id}.json", id)
                    .retrieve()
                    .body(FirebaseStoryItem.class);

            if (item == null || !"story".equals(item.type())) {
                return null;
            }

            // Fallback for missing text or URL
            String text = item.text() != null ? item.text() : (item.title() != null ? item.title() : "");
            String url = item.url() != null ? item.url() : "https://news.ycombinator.com/item?id=" + id;

            return new NewsStory(
                    id,
                    item.title(),
                    url,
                    item.by(),
                    item.score(),
                    text
            );
        } catch (Exception e) {
            // Log warning and return null to gracefully skip failed stories
            return null;
        }
    }
}

// Support Records
record AlgoliaSearchResponse(List<AlgoliaHit> hits) {}
record AlgoliaHit(String objectID) {}
record FirebaseStoryItem(
    String id,
    String type,
    String by,
    int score,
    String title,
    String text,
    String url
) {}
