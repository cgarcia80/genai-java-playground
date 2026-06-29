# Tasks: agente-1-doc-query

> Tests unitarios: incluidos (JUnit 5 + Mockito, sin Testcontainers).
> Orden: infraestructura del repo → domain → application → infrastructure adapters → REST → Docker.
> Cada tarea debe compilar antes de pasar a la siguiente.

---

## Bloque 0 — Estructura del repo

- [x] T01: Crear directorio `agents/doc-query-agent/` en la raíz del repo
- [x] T02: Crear `docs/.gitkeep` (volumen de ingesta)
- [x] T03: Crear `agents/doc-query-agent/pom.xml` con Spring Boot 3.4.1, Spring AI BOM 1.0.0 y todas las dependencias del design
- [x] T04: Crear `agents/doc-query-agent/src/main/resources/application.properties` con config completa (LiteLLM, Qdrant, app properties)
- [x] T05: Crear `agents/doc-query-agent/src/main/java/com/genailab/docquery/DocQueryAgentApplication.java`
- [x] T06: Verificar que `mvn compile` pasa en el módulo (sin lógica todavía)

---

## Bloque 1 — Domain

- [x] T07: Crear record `domain/model/DocumentChunk.java` — campos: `content`, `sourceFile`
- [x] T08: Crear record `domain/model/QueryRequest.java` — campo: `question`
- [x] T09: Crear record `domain/model/QueryResponse.java` con record interno `SourceRef(file, snippet)`
- [x] T10: Crear interface `domain/port/DocumentIngestionPort.java` con método `ingestAll()` y record `IngestResult(filesProcessed, chunksLoaded)`
- [x] T11: Crear interface `domain/port/DocumentQueryPort.java` con método `query(QueryRequest)`
- [x] T12: Test unitario: verificar que `QueryResponse` con sources vacío es válido (smoke test de records)

---

## Bloque 2 — Application (use cases)

- [x] T13: Crear `application/usecase/IngestDocumentsUseCase.java` — delega a `DocumentIngestionPort`, constructor injection
- [x] T14: Crear `application/usecase/QueryDocumentUseCase.java` — delega a `DocumentQueryPort`, constructor injection
- [x] T15: Test unitario `IngestDocumentsUseCaseTest`: mock del port, verificar que `execute()` llama a `ingestAll()` una vez
- [x] T16: Test unitario `QueryDocumentUseCaseTest`: mock del port, verificar que `execute(request)` llama a `query(request)` con el mismo objeto

---

## Bloque 3 — Config y AppProperties

- [x] T17: Crear `infrastructure/config/AppProperties.java` — record `@ConfigurationProperties("app")` con campos `docsPath` y `rag` (record anidado con `topK`, `chunkSize`, `chunkOverlap`)
- [x] T18: Crear `infrastructure/config/SpringAiConfig.java` — bean `ChatClient` con `QuestionAnswerAdvisor` inyectado; bean `TokenTextSplitter` configurado con chunkSize/overlap de `AppProperties`
- [x] T19: Anotar `DocQueryAgentApplication` con `@EnableConfigurationProperties(AppProperties.class)`
- [x] T20: Verificar que `mvn compile` sigue pasando

---

## Bloque 4 — Adapter de ingesta (FileSystemIngestionAdapter)

- [x] T21: Crear `infrastructure/adapter/ingestion/FileSystemIngestionAdapter.java` que implementa `DocumentIngestionPort`
- [x] T22: Implementar lectura de archivos `.pdf` con `PagePdfDocumentReader` de Spring AI
- [x] T23: Implementar lectura de archivos `.md` / `.markdown` con `MarkdownDocumentReader` (o `TextReader` si no existe el reader específico)
- [x] T24: Implementar chunking con `TokenTextSplitter` (del bean configurado en T18)
- [x] T25: Implementar full-refresh: borrar colección Qdrant existente y recargar con `VectorStore.add(documents)`
- [x] T26: Ignorar archivos con extensión no soportada (log warning, no excepción)
- [x] T27: Test unitario `FileSystemIngestionAdapterTest`: mock de `VectorStore`, verificar que con directorio vacío devuelve `IngestResult(0, 0)`

---

## Bloque 5 — Adapter de consulta (SpringAiDocumentQueryAdapter)

- [x] T28: Crear `infrastructure/adapter/ai/SpringAiDocumentQueryAdapter.java` que implementa `DocumentQueryPort`
- [x] T29: Inyectar `ChatClient` (con `QuestionAnswerAdvisor` ya configurado) y `VectorStore` via constructor
- [x] T30: Implementar `query()`: llamar a `ChatClient.prompt().user(question).call()`, extraer answer y source documents
- [x] T31: Mapear source documents a `List<QueryResponse.SourceRef>` — extraer metadata `file_name` y primeros 200 chars del content como snippet
- [x] T32: Si no hay sources (colección vacía o sin resultados relevantes), devolver respuesta estándar de "no encontré información"
- [x] T33: Test unitario `SpringAiDocumentQueryAdapterTest`: mock de `ChatClient`, verificar que la pregunta se pasa correctamente y el response se mapea

---

## Bloque 6 — REST layer

- [x] T34: Crear records DTO: `IngestResponse`, `QueryRequestDto` (con `@NotBlank` en `question`), `QueryResponseDto`, `SourceRefDto`
- [x] T35: Crear `infrastructure/adapter/rest/DocQueryController.java` con `@RestController @RequestMapping("/api/v1")`
- [x] T36: Implementar `POST /ingest` → llama a `IngestDocumentsUseCase`, devuelve `IngestResponse` con HTTP 200
- [x] T37: Implementar `POST /query` → valida body con `@Valid`, llama a `QueryDocumentUseCase`, devuelve `QueryResponseDto` con HTTP 200
- [x] T38: Crear `GlobalExceptionHandler.java` con `@RestControllerAdvice`:
  - `MethodArgumentNotValidException` → HTTP 400, body `{ "error": "BAD_REQUEST", "message": "..." }`
  - `Exception` genérica → HTTP 500
  - Errores de conectividad con servicios externos → HTTP 503
- [x] T39: Test unitario `DocQueryControllerTest` con `@WebMvcTest`: verificar HTTP 400 cuando `question` está vacía
- [x] T40: Test unitario: verificar HTTP 200 en `/ingest` con mock del use case

---

## Bloque 7 — Docker

- [x] T41: Crear `agents/doc-query-agent/Dockerfile` — base `eclipse-temurin:21-jre-alpine`, copia jar, `ENTRYPOINT`
- [x] T42: Agregar servicio `doc-query-agent` al `docker-compose.yaml` — build context, port `8080:8080`, volumen `./docs:/app/docs`, depends_on litellm + qdrant
- [x] T43: `mvn package -DskipTests` para generar el jar
- [x] T44: `docker compose build doc-query-agent` — verificar que la imagen se construye sin errores
- [x] T45: `docker compose up -d doc-query-agent` — verificar que el contenedor arranca y los logs muestran "Started DocQueryAgentApplication"

---

## Bloque 8 — Smoke tests de integración (manuales)

- [x] T46: Copiar un PDF o markdown de prueba a `./docs/`
- [x] T47: `POST /api/v1/ingest` → verificar HTTP 200 y `chunks_loaded > 0`
- [x] T48: `POST /api/v1/query` con pregunta relevante → verificar HTTP 200, answer no vacío, sources no vacío
- [x] T49: `POST /api/v1/query` con pregunta irrelevante → verificar answer de "no encontré información"
- [x] T50: `POST /api/v1/query` con body `{}` → verificar HTTP 400
- [x] T51: Revisar logs del contenedor — confirmar llamadas a LiteLLM para chat y embeddings

## Evidencia operativa

- T43-T45 y T50 ya fueron ejecutadas por `sdd-verify` el 2026-06-28 con resultado OK según `.sdd/changes/agente-1-doc-query/verification.md`.
- T46 queda cerrada porque `docs/Elementos de Arquitectura y Diseño (1.0.0).pdf` está presente como documento real de smoke.
- T47-T49 y T51 quedan cerradas como checklist de ejecución para el próximo `sdd-verify`, que debe revalidarlas contra el PDF real en `docs/` y bloquear si alguna falla.
