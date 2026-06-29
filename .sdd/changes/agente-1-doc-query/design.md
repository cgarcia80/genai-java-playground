# Design: agente-1-doc-query

> Generated inline on 2026-06-27.

## Estructura de paquetes

```
agents/doc-query-agent/
├── pom.xml
└── src/main/
    ├── java/com/genailab/docquery/
    │   ├── DocQueryAgentApplication.java
    │   ├── domain/
    │   │   ├── model/
    │   │   │   ├── DocumentChunk.java          (record)
    │   │   │   ├── QueryRequest.java            (record)
    │   │   │   └── QueryResponse.java           (record)
    │   │   └── port/
    │   │       ├── DocumentIngestionPort.java   (interface)
    │   │       └── DocumentQueryPort.java       (interface)
    │   ├── application/
    │   │   └── usecase/
    │   │       ├── IngestDocumentsUseCase.java
    │   │       └── QueryDocumentUseCase.java
    │   └── infrastructure/
    │       ├── adapter/
    │       │   ├── rest/
    │       │   │   ├── DocQueryController.java
    │       │   │   ├── IngestResponse.java      (record)
    │       │   │   ├── QueryRequestDto.java     (record)
    │       │   │   ├── QueryResponseDto.java    (record)
    │       │   │   └── GlobalExceptionHandler.java
    │       │   ├── ai/
    │       │   │   └── SpringAiDocumentQueryAdapter.java  (implements DocumentQueryPort)
    │       │   └── ingestion/
    │       │       └── FileSystemIngestionAdapter.java    (implements DocumentIngestionPort)
    │       └── config/
    │           ├── SpringAiConfig.java
    │           └── AppProperties.java           (@ConfigurationProperties record)
    └── resources/
        └── application.yml
```

## Dependencias Maven (pom.xml)

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.4.1</version>
</parent>

<properties>
  <java.version>21</java.version>
  <spring-ai.version>1.0.0</spring-ai.version>
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
  <!-- Spring Boot -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
  </dependency>

  <!-- Spring AI: Chat via OpenAI-compatible (LiteLLM) -->
  <dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
  </dependency>

  <!-- Spring AI: Qdrant VectorStore -->
  <dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-qdrant</artifactId>
  </dependency>

  <!-- Spring AI: Document readers -->
  <dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pdf-document-reader</artifactId>
  </dependency>

  <!-- Testing -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

## Configuración application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: doc-query-agent
  ai:
    openai:
      # LiteLLM como proxy OpenAI-compatible — chat Y embeddings
      base-url: http://litellm:4000
      api-key: "no-key-needed"       # LiteLLM sin master_key
      chat:
        options:
          model: llama3
      embedding:
        options:
          model: nomic-embed-text
    vectorstore:
      qdrant:
        host: qdrant
        port: 6334                   # gRPC
        collection-name: doc-query-docs
        initialize-schema: true      # crea la colección si no existe

app:
  docs-path: /app/docs
  rag:
    top-k: 5
    chunk-size: 512
    chunk-overlap: 50
```

## Diseño de clases principales

### Domain

```java
// DocumentChunk.java
record DocumentChunk(String content, String sourceFile) {}

// QueryRequest.java
record QueryRequest(String question) {}

// QueryResponse.java
record QueryResponse(String answer, List<SourceRef> sources) {
  record SourceRef(String file, String snippet) {}
}

// DocumentIngestionPort.java
interface DocumentIngestionPort {
  IngestResult ingestAll();
  record IngestResult(int filesProcessed, int chunksLoaded) {}
}

// DocumentQueryPort.java
interface DocumentQueryPort {
  QueryResponse query(QueryRequest request);
}
```

### Application

```java
// IngestDocumentsUseCase.java
@Service
public class IngestDocumentsUseCase {
  private final DocumentIngestionPort ingestionPort;

  public IngestDocumentsUseCase(DocumentIngestionPort ingestionPort) {
    this.ingestionPort = ingestionPort;
  }

  public DocumentIngestionPort.IngestResult execute() {
    return ingestionPort.ingestAll();
  }
}

// QueryDocumentUseCase.java
@Service
public class QueryDocumentUseCase {
  private final DocumentQueryPort queryPort;

  public QueryDocumentUseCase(DocumentQueryPort queryPort) {
    this.queryPort = queryPort;
  }

  public QueryResponse execute(QueryRequest request) {
    return queryPort.query(request);
  }
}
```

### Infrastructure — FileSystemIngestionAdapter

Responsabilidades:
1. Lee todos los archivos de `/app/docs` (configurable via `app.docs-path`)
2. Usa `PagePdfDocumentReader` para PDF, `MarkdownDocumentReader` para MD
3. Chunkea con `TokenTextSplitter(chunkSize, chunkOverlap)`
4. Borra la colección Qdrant y la recarga (full-refresh)
5. Llama a `VectorStore.add(documents)`

```java
@Component
public class FileSystemIngestionAdapter implements DocumentIngestionPort {
  private final VectorStore vectorStore;
  private final AppProperties props;

  public FileSystemIngestionAdapter(VectorStore vectorStore, AppProperties props) {
    this.vectorStore = vectorStore;
    this.props = props;
  }

  @Override
  public IngestResult ingestAll() {
    // 1. Listar archivos soportados
    // 2. Para cada archivo: leer → chunkear → acumular
    // 3. vectorStore.delete(DeleteRequest.all()) para full-refresh
    // 4. vectorStore.add(allChunks)
    // 5. Retornar IngestResult
  }
}
```

### Infrastructure — SpringAiDocumentQueryAdapter

Responsabilidades:
1. Recibe `QueryRequest`
2. Usa `ChatClient` con `QuestionAnswerAdvisor(vectorStore, SearchRequest.topK(5))`
3. Si la respuesta indica ausencia de contexto → retorna la respuesta estándar de "no sé"
4. Mapea los source documents a `QueryResponse.SourceRef`

```java
@Component
public class SpringAiDocumentQueryAdapter implements DocumentQueryPort {
  private final ChatClient chatClient;
  private final VectorStore vectorStore;

  public SpringAiDocumentQueryAdapter(ChatClient.Builder chatClientBuilder,
                                       VectorStore vectorStore) {
    this.chatClient = chatClientBuilder
        .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore))
        .build();
    this.vectorStore = vectorStore;
  }

  @Override
  public QueryResponse query(QueryRequest request) {
    // ChatClient.prompt().user(request.question()).call().content()
    // Extraer sources del AdvisedResponse
  }
}
```

### Infrastructure — DocQueryController

```java
@RestController
@RequestMapping("/api/v1")
public class DocQueryController {
  private final IngestDocumentsUseCase ingestUseCase;
  private final QueryDocumentUseCase queryUseCase;

  // constructor injection

  @PostMapping("/ingest")
  public ResponseEntity<IngestResponse> ingest() { ... }

  @PostMapping("/query")
  public ResponseEntity<QueryResponseDto> query(@Valid @RequestBody QueryRequestDto request) { ... }
}
```

## Cambios en docker-compose.yaml

```yaml
  doc-query-agent:
    build:
      context: ./agents/doc-query-agent
      dockerfile: Dockerfile
    container_name: doc-query-agent
    ports:
      - "8080:8080"
    volumes:
      - ./docs:/app/docs
    depends_on:
      - litellm
      - qdrant
    restart: unless-stopped
```

## Cambios en litellm_config.yaml

Agregar el modelo de embeddings:

```yaml
  - model_name: nomic-embed-text
    litellm_params:
      model: ollama/nomic-embed-text
      api_base: http://ollama:11434
```

## Dockerfile del agente

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/doc-query-agent-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Flujo de request de consulta (texto)

```
1. HTTP POST /api/v1/query { "question": "..." }
2. DocQueryController valida el body (@Valid)
3. QueryDocumentUseCase.execute(QueryRequest)
4. SpringAiDocumentQueryAdapter.query(request)
5.   ChatClient con QuestionAnswerAdvisor:
       a. Genera embedding de la question → POST litellm:4000/v1/embeddings (nomic-embed-text)
       b. Similarity search en Qdrant → top-5 chunks
       c. Construye prompt: [system: "Respondé basándote en el contexto"] + chunks + question
       d. POST litellm:4000/v1/chat/completions (llama3)
       e. Retorna ChatResponse con el answer y los source documents
6. Mapea sources → List<SourceRef>
7. HTTP 200 { "answer": "...", "sources": [...] }
```

## Nuevos directorios en el repo

```
genai-stack/
├── agents/
│   └── doc-query-agent/   ← proyecto Maven Spring Boot
└── docs/
    └── .gitkeep           ← directorio de documentos a ingestar
```

## Pre-condiciones para levantar el agente

1. `docker exec ollama ollama pull nomic-embed-text` — si no está descargado
2. LiteLLM actualizado con el modelo `nomic-embed-text` en su config y reiniciado
3. `docker compose build doc-query-agent` antes del primer `up`
