# Design: news-curator-agent

Hexagonal microservice running on port `8084` that implements dynamic news ingestion and Retrieval-Augmented Generation (RAG) over a Qdrant vector database.

## Package Structure

```
agents/news-curator-agent/
├── pom.xml
└── src/main/
    ├── java/com/genailab/newscurator/
    │   ├── NewsCuratorApplication.java
    │   ├── domain/
    │   │   ├── model/
    │   │   │   └── NewsStory.java               (record)
    │   │   └── port/
    │   │       ├── NewsFetcherPort.java         (interface)
    │   │       └── NewsVectorStorePort.java     (interface)
    │   ├── application/
    │   │   └── usecase/
    │   │       ├── CurateNewsUseCase.java
    │   │       └── QueryNewsUseCase.java
    │   └── infrastructure/
    │       ├── adapter/
    │       │   ├── rest/
    │       │   │   ├── NewsCuratorController.java
    │       │   │   └── Dto.java                 (records: CurateReq, CurateResp, QueryReq, QueryResp)
    │       │   ├── hn/
    │       │   │   ├── HackerNewsRestAdapter.java (implements NewsFetcherPort)
    │       │   │   └── AlgoliaSearchResponse.java (record helper for parsing Algolia)
    │       │   └── vectorstore/
    │       │       └── QdrantVectorStoreAdapter.java (implements NewsVectorStorePort)
    │       └── config/
    │           ├── HackerNewsProperties.java    (@ConfigurationProperties record)
    │           ├── AiConfig.java                (Spring AI Configuration)
    │           └── ThreadPoolConfig.java        (Executor for parallel requests)
    └── resources/
        └── application.yml
```

---

## Technical Decisions

### D1: External APIs Configuration via `@ConfigurationProperties` record
We configure connection endpoints and properties for the HackerNews integrations (Algolia Search API & Firebase HN API) in a strongly-typed, immutable record mapped to the prefix `app.hacker-news`.

```java
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
```

### D2: Concurrent Integration via RestClient & CompletableFuture
To curate news, we first call the Algolia Search API to retrieve the story IDs matching the keyword/topic (up to the requested `limit`).
Since Algolia does not return the full details (like current score or full text) reliably, we fetch each story detail from the official Firebase HN API.
To prevent sequential blocking bottleneck, we execute calls to `https://hacker-news.firebaseio.com/v0/item/{id}.json` in parallel using `CompletableFuture` combined with a dedicated Thread Pool.

```java
// ThreadPoolConfig.java configuration of the Executor
@Bean(name = "hnTaskExecutor")
public Executor hnTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("hn-fetch-");
    executor.initialize();
    return executor;
}
```

Implementation logic flow in `HackerNewsRestAdapter`:
```java
public List<NewsStory> fetchStories(String topic, int limit) {
    // 1. Fetch story IDs from Algolia: GET /search?query={topic}&tags=story&numericFilters=created_at_i>...
    List<String> storyIds = fetchIdsFromAlgolia(topic, limit);

    // 2. Fetch details concurrently
    List<CompletableFuture<NewsStory>> futures = storyIds.stream()
        .map(id -> CompletableFuture.supplyAsync(() -> fetchStoryDetail(id), hnTaskExecutor))
        .toList();

    return futures.stream()
        .map(CompletableFuture::join)
        .filter(Objects::nonNull)
        .toList();
}
```

### D3: Local Embeddings Model `nomic-embed-text` via LiteLLM
Consistent with `doc-query-agent`, we use the local embeddings model `nomic-embed-text` served through a LiteLLM proxy at `http://litellm:4000`. The LLM client for RAG synthesis points to `gpt-4o-mini` configured either directly or routed through LiteLLM.

```yaml
spring:
  ai:
    openai:
      base-url: http://litellm:4000
      api-key: "no-key-needed"
      chat:
        options:
          model: gpt-4o-mini
      embedding:
        options:
          model: nomic-embed-text
```

### D4: Qdrant Vector DB Collection and Payload Schema
We store stories in a Qdrant collection named `news-curator`.
For each story, we store its vector (dimension size depending on `nomic-embed-text`, which is 768) and the following metadata payload:
*   `title` (string)
*   `url` (string)
*   `author` (string)
*   `score` (integer)
*   `text` (string) - the text content that represents the story (either story text, or title combined with metadata).

We map this to Spring AI's `Document` structure:
```java
Document document = new Document(
    story.text(), 
    Map.of(
        "title", story.title(),
        "url", story.url(),
        "author", story.author(),
        "score", story.score()
    )
);
```

### D5: RAG Prompt System for strict Source Citation & Lack-of-Context handling
We craft a system prompt for the OpenAI Chat client (`gpt-4o-mini`) that strictly guides the model on context utilization:
1.  Must answer using **only** the provided HackerNews context.
2.  Must cite sources explicitly (using `title` and `url`).
3.  If the context has no relevant stories or is insufficient, it must politely explain that it lacks local context to answer, rather than hallucinating or using general LLM knowledge.

```
You are a helpful, expert AI assistant. Your task is to answer the user's question using ONLY the provided HackerNews stories context.

Rules:
1. Base your answer solely on the context provided. Do not use external knowledge or make assumptions.
2. For any fact you state, you MUST explicitly cite its source by mentioning the title and the exact URL of the story. Format citations naturally (e.g., "[Title](URL)").
3. If the context is empty, or does not contain enough information to answer the question, you must respond politely stating: "I'm sorry, but I couldn't find any relevant local context from HackerNews stories to answer your question."
4. Do not include any sources or URLs in the sources list if they are not part of the retrieved context.
```

---

## Component Shapes (Hexagonal Architecture)

### Domain Layer

```java
// NewsStory.java
package com.genailab.newscurator.domain.model;

public record NewsStory(
    String id,
    String title,
    String url,
    String author,
    int score,
    String text
) {}

// NewsFetcherPort.java
package com.genailab.newscurator.domain.port;

import com.genailab.newscurator.domain.model.NewsStory;
import java.util.List;

public interface NewsFetcherPort {
    List<NewsStory> fetchStories(String topic, int limit);
}

// NewsVectorStorePort.java
package com.genailab.newscurator.domain.port;

import com.genailab.newscurator.domain.model.NewsStory;
import java.util.List;

public interface NewsVectorStorePort {
    void saveStories(List<NewsStory> stories);
    List<NewsStory> searchSimilar(String query, int limit);
}
```

### Application Layer

```java
// CurateNewsUseCase.java
package com.genailab.newscurator.application.usecase;

import com.genailab.newscurator.domain.model.NewsStory;
import com.genailab.newscurator.domain.port.NewsFetcherPort;
import com.genailab.newscurator.domain.port.NewsVectorStorePort;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CurateNewsUseCase {
    private final NewsFetcherPort fetcherPort;
    private final NewsVectorStorePort vectorStorePort;

    public CurateNewsUseCase(NewsFetcherPort fetcherPort, NewsVectorStorePort vectorStorePort) {
        this.fetcherPort = fetcherPort;
        this.vectorStorePort = vectorStorePort;
    }

    public int curate(String topic, int limit) {
        List<NewsStory> stories = fetcherPort.fetchStories(topic, limit);
        if (!stories.isEmpty()) {
            vectorStorePort.saveStories(stories);
        }
        return stories.size();
    }
}
```

```java
// QueryNewsUseCase.java
package com.genailab.newscurator.application.usecase;

import com.genailab.newscurator.domain.model.NewsStory;
import com.genailab.newscurator.domain.port.NewsVectorStorePort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class QueryNewsUseCase {
    private final NewsVectorStorePort vectorStorePort;
    private final ChatClient chatClient;
    private static final String SYSTEM_PROMPT = """
        You are a helpful, expert AI assistant. Your task is to answer the user's question using ONLY the provided HackerNews stories context.
        
        Rules:
        1. Base your answer solely on the context provided. Do not use external knowledge or make assumptions.
        2. For any fact you state, you MUST explicitly cite its source by mentioning the title and the exact URL of the story. Format citations naturally (e.g., "[Title](URL)").
        3. If the context is empty, or does not contain enough information to answer the question, you must respond politely stating: "I'm sorry, but I couldn't find any relevant local context from HackerNews stories to answer your question."
        """;

    public QueryNewsUseCase(NewsVectorStorePort vectorStorePort, ChatClient.Builder chatClientBuilder) {
        this.vectorStorePort = vectorStorePort;
        this.chatClient = chatClientBuilder.build();
    }

    public QueryResult execute(String question) {
        // Retrieve top 5 matching items
        List<NewsStory> contextStories = vectorStorePort.searchSimilar(question, 5);
        
        String contextText = contextStories.stream()
            .map(s -> String.format("Title: %s\nURL: %s\nAuthor: %s\nScore: %d\nContent: %s\n---", 
                s.title(), s.url(), s.author(), s.score(), s.text()))
            .collect(Collectors.joining("\n"));

        String answer = chatClient.prompt()
            .system(sp -> sp.text(SYSTEM_PROMPT))
            .user(u -> u.text("Context:\n" + contextText + "\n\nQuestion: " + question))
            .call()
            .content();

        List<SourceDto> sources = contextStories.stream()
            .map(s -> new SourceDto(s.title(), s.url()))
            .toList();

        return new QueryResult(answer, sources);
    }

    public record QueryResult(String answer, List<SourceDto> sources) {}
    public record SourceDto(String title, String url) {}
}
```

### Infrastructure Layer

#### Controllers & DTOs
```java
// NewsCuratorController.java
package com.genailab.newscurator.infrastructure.adapter.rest;

import com.genailab.newscurator.application.usecase.CurateNewsUseCase;
import com.genailab.newscurator.application.usecase.QueryNewsUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class NewsCuratorController {
    private final CurateNewsUseCase curateUseCase;
    private final QueryNewsUseCase queryUseCase;

    public NewsCuratorController(CurateNewsUseCase curateUseCase, QueryNewsUseCase queryUseCase) {
        this.curateUseCase = curateUseCase;
        this.queryUseCase = queryUseCase;
    }

    @PostMapping("/curate")
    public ResponseEntity<CurateResponse> curate(@Valid @RequestBody CurateRequest request) {
        int itemsCurated = curateUseCase.curate(request.topic(), request.limit());
        return ResponseEntity.ok(new CurateResponse("success", itemsCurated));
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        QueryNewsUseCase.QueryResult result = queryUseCase.execute(request.question());
        return ResponseEntity.ok(new QueryResponse(result.answer(), result.sources()));
    }
}

// Request/Response Records
record CurateRequest(
    @NotBlank(message = "Topic is required") String topic,
    @NotNull(message = "Limit is required") @Min(value = 1, message = "Limit must be positive") Integer limit
) {}

record CurateResponse(String status, int itemsCurated) {}

record QueryRequest(
    @NotBlank(message = "Question is required") String question
) {}

record QueryResponse(
    String answer,
    List<QueryNewsUseCase.SourceDto> sources
) {}
```

#### HackerNewsRestAdapter
```java
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
```

#### QdrantVectorStoreAdapter
```java
package com.genailab.newscurator.infrastructure.adapter.vectorstore;

import com.genailab.newscurator.domain.model.NewsStory;
import com.genailab.newscurator.domain.port.NewsVectorStorePort;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class QdrantVectorStoreAdapter implements NewsVectorStorePort {
    private final VectorStore vectorStore;

    public QdrantVectorStoreAdapter(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void saveStories(List<NewsStory> stories) {
        List<Document> documents = stories.stream()
                .map(s -> new Document(
                        s.text(),
                        Map.of(
                                "id", s.id(),
                                "title", s.title(),
                                "url", s.url(),
                                "author", s.author(),
                                "score", s.score()
                        )
                ))
                .toList();
        vectorStore.add(documents);
    }

    @Override
    public List<NewsStory> searchSimilar(String query, int limit) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.query(query).withTopK(limit)
        );

        return results.stream()
                .map(doc -> {
                    Map<String, Object> metadata = doc.getMetadata();
                    return new NewsStory(
                            (String) metadata.getOrDefault("id", doc.getId()),
                            (String) metadata.getOrDefault("title", "No Title"),
                            (String) metadata.getOrDefault("url", ""),
                            (String) metadata.getOrDefault("author", "anonymous"),
                            ((Number) metadata.getOrDefault("score", 0)).intValue(),
                            doc.getContent()
                    );
                })
                .toList();
    }
}
```

---

## Maven Dependencies (`pom.xml` configurations)

The `news-curator-agent` is a new microservice. It uses Maven, inheriting from `spring-boot-starter-parent` 3.4.1 (same as `doc-query-agent`).

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.4.1</version>
</parent>

<properties>
  <java.version>21</java.version>
  <spring-ai.version>1.0.0-M1</spring-ai.version>
</properties>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-bom</artifactId>
      <version>${spring-ai.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <!-- Spring Boot Web & Validation -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
  </dependency>

  <!-- Spring AI: OpenAI client for gpt-4o-mini & nomic-embed-text embeddings -->
  <dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
  </dependency>

  <!-- Spring AI: Qdrant VectorStore support -->
  <dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-qdrant</artifactId>
  </dependency>

  <!-- Testing -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

---

## Application configuration (`application.yml`)

```yaml
server:
  port: 8084

spring:
  application:
    name: news-curator-agent
  ai:
    openai:
      base-url: http://litellm:4000
      api-key: "no-key-needed"
      chat:
        options:
          model: gpt-4o-mini
      embedding:
        options:
          model: nomic-embed-text
    vectorstore:
      qdrant:
        host: qdrant
        port: 6334
        collection-name: news-curator
        initialize-schema: true

app:
  hacker-news:
    algolia-base-url: https://hn.algolia.com/api/v1
    firebase-base-url: https://hacker-news.firebaseio.com/v0
    connect-timeout-ms: 5000
    read-timeout-ms: 10000
```
