# Tasks: news-curator-agent

Implementation checklist for the `news-curator-agent` change, organized across 8 implementation phases.

---

## Phase 1: Scaffolding

Set up the project structure and build framework for the new microservice.

- [x] **Task 1.1**: Create the Maven project file `pom.xml` for `news-curator-agent`.
  - **File**: [pom.xml](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/pom.xml)
  - **Details**: Declare parent POM (matching Spring Boot version 3.4.1), dependencies (`spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-ai-starter-model-openai`, `spring-ai-starter-vector-store-qdrant`, and `spring-boot-starter-test` for testing), and the Spring AI BOM dependency management.
  - **Validation**: Check that Maven can parse the configuration and build dependencies without resolving compilation blocks.

- [x] **Task 1.2**: Create the application Docker container setup.
  - **File**: [Dockerfile](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/Dockerfile)
  - **Details**: Define multi-stage build using Eclipse Temurin JDK 21 (builder) and JRE 21 (runner). Expose port 8084.
  - **Validation**: Dockerfile compiles and exposes 8084 in the image metadata.

- [x] **Task 1.3**: Create the Spring Boot main application class.
  - **File**: [NewsCuratorApplication.java](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/main/java/com/genailab/newscurator/NewsCuratorApplication.java)
  - **Details**: Create standard Spring Boot main application entrypoint with `@SpringBootApplication` and `@ConfigurationPropertiesScan` annotations to scan configuration records.
  - **Validation**: Class compiles and can be launched without immediate runtime errors (aside from lack of config files).

---

## Phase 2: Domain

Define the core domain model and ports following the hexagonal architecture rules.

- [x] **Task 2.1**: Implement the main domain entity model.
  - **File**: [NewsStory.java](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/main/java/com/genailab/newscurator/domain/model/NewsStory.java)
  - **Details**: Create the Java `record NewsStory` containing `id` (String), `title` (String), `url` (String), `author` (String), `score` (int), and `text` (String). Ensure zero external framework annotations in this package.
  - **Validation**: Source code compiles and contains correct attributes.

- [x] **Task 2.2**: Define the fetcher port interface.
  - **File**: [NewsFetcherPort.java](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/main/java/com/genailab/newscurator/domain/port/NewsFetcherPort.java)
  - **Details**: Create `interface NewsFetcherPort` with method `List<NewsStory> fetchStories(String topic, int limit)`.
  - **Validation**: Compiles successfully.

- [x] **Task 2.3**: Define the vector store port interface.
  - **File**: [NewsVectorStorePort.java](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/main/java/com/genailab/newscurator/domain/port/NewsVectorStorePort.java)
  - **Details**: Create `interface NewsVectorStorePort` with methods `void saveStories(List<NewsStory> stories)` and `List<NewsStory> searchSimilar(String query, int limit)`.
  - **Validation**: Compiles successfully.

---

## Phase 3: Application

Implement the core use case business logic and write unit tests mock-testing the ports.

- [x] **Task 3.1**: Implement news curation orchestration use case.
  - **File**: [CurateNewsUseCase.java](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/main/java/com/genailab/newscurator/application/usecase/CurateNewsUseCase.java)
  - **Details**: Implement class annotated with `@Service` injecting `NewsFetcherPort` and `NewsVectorStorePort`. Perform fetching and conditionally call vector store save if stories are found. Return total items curated.
  - **Validation**: Compiles successfully.

- [x] **Task 3.2**: Implement query and RAG synthesis usecase.
  - **File**: [QueryNewsUseCase.java](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/main/java/com/genailab/newscurator/application/usecase/QueryNewsUseCase.java)
  - **Details**: Implement class annotated with `@Service` injecting `NewsVectorStorePort` and `ChatClient.Builder`. In `execute(String question)` method, query vector store for top 5 matches, construct a prompt strictly specifying citation instructions and lack-of-context handling guidelines (Rule D5), fetch LLM result, and return `QueryResult` wrapping answer and sources.
  - **Validation**: Compiles successfully.

- [x] **Task 3.3**: Write unit test for curate news usecase.
  - **File**: [CurateNewsUseCaseTest.java](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/test/java/com/genailab/newscurator/application/usecase/CurateNewsUseCaseTest.java)
  - **Details**: Write JUnit 5 unit tests with Mockito (`@ExtendWith(MockitoExtension.class)`). Verify that mock fetcher is called and mock vector store saves stories successfully when stories are returned, or is skipped when no stories are found.
  - **Validation**: Run unit test locally.

- [x] **Task 3.4**: Write unit test for query news usecase.
  - **File**: [QueryNewsUseCaseTest.java](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/test/java/com/genailab/newscurator/application/usecase/QueryNewsUseCaseTest.java)
  - **Details**: Write JUnit 5 unit tests verifying similar vector store query execution, prompt format compliance, and output generation matching mocks.
  - **Validation**: Run unit test locally.

---

## Phase 4: Configuration

Establish environment-specific settings, properties classes, and dependency-injection definitions.

- [x] **Task 4.1**: Create HackerNews integration properties record.
  - **File**: [HackerNewsProperties.java](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/main/java/com/genailab/newscurator/infrastructure/config/HackerNewsProperties.java)
  - **Details**: Write record mapped to `@ConfigurationProperties(prefix = "app.hacker-news")` holding URLs and timeout configurations.
  - **Validation**: Compiles successfully.

- [x] **Task 4.2**: Configure Spring AI components.
  - **File**: [AiConfig.java](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/main/java/com/genailab/newscurator/infrastructure/config/AiConfig.java)
  - **Details**: Expose a `@Bean` for `ChatClient.Builder` using default auto-configured clients, ensuring configuration binds properly.
  - **Validation**: Compiles successfully.

- [x] **Task 4.3**: Create Thread Pool configurations for concurrent HN requests.
  - **File**: [ThreadPoolConfig.java](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/main/java/com/genailab/newscurator/infrastructure/config/ThreadPoolConfig.java)
  - **Details**: Expose a `@Bean` of type `Executor` named `hnTaskExecutor` initialized with a core pool size of 10, max pool size of 20, queue capacity of 100, and thread prefix `hn-fetch-`.
  - **Validation**: Compiles successfully.

- [x] **Task 4.4**: Write application environment YAML configuration.
  - **File**: [application.yml](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/main/resources/application.yml)
  - **Details**: Define app server port (8084), Spring application name, Spring AI parameters pointing to LiteLLM server (`http://litellm:4000`), model configurations (`gpt-4o-mini`, `nomic-embed-text`), Qdrant server integration (`host: qdrant`, `port: 6334`, `collection-name: news-curator`), and default HackerNews properties parameters.
  - **Validation**: YAML is clean and parses correctly.

---

## Phase 5: Adapters

Create actual implementation infrastructure classes mapping ports to external services.

- [x] **Task 5.1**: Implement HackerNews client rest adapter.
  - **File**: [HackerNewsRestAdapter.java](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/main/java/com/genailab/newscurator/infrastructure/adapter/hn/HackerNewsRestAdapter.java)
  - **Details**: Implement `NewsFetcherPort` using `RestClient` instances to query the Algolia Search API first to fetch story IDs, and then make concurrent HTTP requests to Firebase HN API detail endpoints using `CompletableFuture.supplyAsync` with `hnTaskExecutor`.
  - **Validation**: Compiles and resolves concurrency without deadlock risks.

- [x] **Task 5.2**: Implement Qdrant vector database adapter.
  - **File**: [QdrantVectorStoreAdapter.java](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/main/java/com/genailab/newscurator/infrastructure/adapter/vectorstore/QdrantVectorStoreAdapter.java)
  - **Details**: Implement `NewsVectorStorePort` wrapping Spring AI's `VectorStore` bean, mapping `NewsStory` elements to Spring AI `Document` metadata properties payload, and vice versa.
  - **Validation**: Compiles successfully.

- [x] **Task 5.3**: Write unit and mock server tests for HN rest adapter.
  - **File**: [HackerNewsRestAdapterTest.java](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/test/java/com/genailab/newscurator/infrastructure/adapter/hn/HackerNewsRestAdapterTest.java)
  - **Details**: Use `MockRestServiceServer` to test `HackerNewsRestAdapter`. Mock Algolia endpoint response (returning story IDs list) and subsequent concurrent Firebase item endpoints requests. Validate proper conversion to `NewsStory` domain elements.
  - **Validation**: Test runs successfully.

- [x] **Task 5.4**: Write unit test for Qdrant vector store adapter.
  - **File**: [QdrantVectorStoreAdapterTest.java](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/test/java/com/genailab/newscurator/infrastructure/adapter/vectorstore/QdrantVectorStoreAdapterTest.java)
  - **Details**: Use mock `VectorStore` to test metadata extraction and document mapping correctness.
  - **Validation**: Test runs successfully.

---

## Phase 6: REST API

Expose HTTP routes and handle requests/responses validating arguments correctly.

- [x] **Task 6.1**: Create controller and DTO request/response contracts.
  - **File**: [NewsCuratorController.java](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/main/java/com/genailab/newscurator/infrastructure/adapter/rest/NewsCuratorController.java)
  - **Details**: Implement class annotated with `@RestController` and `@RequestMapping("/api/v1")` with endpoints `/curate` (POST) and `/query` (POST). Configure DTO records `CurateRequest` (topic must not be blank, limit must be positive), `CurateResponse`, `QueryRequest` (question must not be blank), and `QueryResponse`.
  - **Validation**: Compiles correctly.

- [x] **Task 6.2**: Write web controller integration/slice tests.
  - **File**: [NewsCuratorControllerTest.java](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/agents/news-curator-agent/src/test/java/com/genailab/newscurator/infrastructure/adapter/rest/NewsCuratorControllerTest.java)
  - **Details**: Use `@WebMvcTest(NewsCuratorController.class)` and `MockMvc`. Mock usecases and perform POST calls checking status codes, JSON payload fields structure, validation rules (blank topic/question should return 400 Bad Request).
  - **Validation**: Run controller tests successfully.

---

## Phase 7: Docker & Parent POM

Register the new agent in the global monorepo and container architecture.

- [x] **Task 7.1**: Declare the module in the root aggregator POM.
  - **File**: [pom.xml](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/pom.xml)
  - **Details**: Add `<module>agents/news-curator-agent</module>` in the root `pom.xml` under `<modules>`.
  - **Validation**: Running `mvn clean compile` from root directory starts building the new agent module.

- [x] **Task 7.2**: Register service in Docker Compose environment configuration.
  - **File**: [docker-compose.yaml](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/docker-compose.yaml)
  - **Details**: Add `news-curator-agent` service definition using the local Dockerfile context. Configure container port mapping `8084:8084`, link to network, depend on `qdrant` and `litellm` services, and provide runtime environment variables matching LiteLLM endpoint configuration.
  - **Validation**: Running `docker compose config` evaluates without syntax errors.

---

## Phase 8: Validation

Run the full end-to-end integration build and execute tests.

- [x] **Task 8.1**: Execute clean Maven build from root.
  - **Details**: Run `mvn clean install` (or `mvn clean package`) on the workspace root to ensure `news-curator-agent` and all other services build and compile without issues.
  - **Validation**: Console output returns `BUILD SUCCESS` for all modules.

- [x] **Task 8.2**: Execute all local unit/integration tests.
  - **Details**: Run `mvn test -pl agents/news-curator-agent` to execute all created tests for this agent.
  - **Validation**: All tests pass successfully (0 failures, 0 errors).
