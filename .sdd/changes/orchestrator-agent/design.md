# Design: orchestrator-agent

> Updated by `sdd-design` on 2026-06-29 after runtime verification.

## Decisions

### D1: Router local rule-based

**Decision**: el orchestrator usa un router deterministico local (`RuleBasedRoutingClassifier`) y no llama a LLM para decidir destino.

**Rationale**: verify demostro que `llama3` local con tool calling se queda colgado; usar Gemini filtraria datos sensibles. Reglas locales respetan privacidad y hacen smoke reproducible.

**Alternatives considered**:
- `llama3` tool calling: descartado por timeout/cuelgue en smoke.
- `gemini-flash`: descartado porque ve logs/documentacion antes de delegar.

**Consequences**: eliminar `ChatClient` del orchestrator, remover dependency Spring AI si queda sin uso, y reemplazar `SpringAiOrchestrationAdapter` por un adapter que clasifica localmente e invoca downstream.

### D2: Reglas de clasificacion

**Decision**: prioridad `diagnosis` > `doc-query` > `smart-search`.

**Rationale**: un stacktrace puede mencionar documentacion o arquitectura, pero si contiene excepcion/log debe diagnosticarse. El default general va a smart-search.

**Consequences**: `RoutingClassifier.classify(String question)` devuelve `RoutingTarget`.

### D3: HTTP clients y timeouts

**Decision**: mantener `AppProperties` y tres `RestClient` dedicados con `connectTimeout=5s` y `readTimeout=90s`.

**Rationale**: el lab local con Ollama puede tardar mas de 30s; 90s evita falsos 502 sin dejar cuelgues indefinidos.

**Consequences**: `application.properties` conserva URLs por env vars y `agents.read-timeout=90s`.

### D4: Respuesta downstream aplanada

**Decision**: el cliente downstream retorna un `String` answer limpio.

**Rationale**: el contrato publico del orchestrator es `{answer, routedTo}`.

**Consequences**: doc-query/smart-search extraen `answer`; diagnosis concatena `rootCause`, `location`, `suggestion`.

### D5: Bypass por header

**Decision**: `X-Bypass-Routing` se procesa en controller y llama a `bypass(request, target)`.

**Rationale**: permite smoke directo y debugging sin pasar por clasificacion.

**Consequences**: valores validos `doc-query`, `diagnosis`, `smart-search`; valor invalido -> 400.

### D6: Fallos downstream

**Decision**: `DownstreamAgentException` se mapea a HTTP 502.

**Rationale**: expresa fallo upstream y evita respuestas inventadas.

**Consequences**: `GlobalExceptionHandler` conserva manejo 400/502/500.

## Component shapes

### RoutingClassifier
- **Location**: `domain/port`
- **Public methods**:
  - `RoutingTarget classify(OrchestrationRequest request)`

### RuleBasedRoutingClassifier
- **Location**: `infrastructure/adapter/routing`
- **Extends / Implements**: `RoutingClassifier`
- **Public methods**:
  - `RoutingTarget classify(OrchestrationRequest request)`

### DownstreamAgentClient
- **Location**: `infrastructure/adapter/http`
- **Public methods**:
  - `String callDocQueryAgent(String question)`
  - `String callDiagnosisAgent(String logContent)`
  - `String callSmartSearchAgent(String question)`
- **Dependencies**: tres `RestClient`.

### OrchestrationAdapter
- **Location**: `infrastructure/adapter/orchestration`
- **Extends / Implements**: `OrchestrationPort`
- **Dependencies**: `RoutingClassifier`, `DownstreamAgentClient`.

## Cross-cutting concerns

- **Transactionality**: N/A, no hay persistencia.
- **Error handling**: validation -> 400, bypass invalido -> 400, downstream -> 502.
- **Idempotency**: N/A, requests sin estado.
- **Observability**: loguear longitud de pregunta y target elegido; no loguear pregunta completa, respuesta ni logContent.
- **Backward compatibility**: conserva API `/api/v1/ask`; cambia solo implementacion interna.
- **Security**: privacidad mejora porque el router no envia input a LLM.

## File changes

| File | Action | Reason |
|------|--------|--------|
| `agents/orchestrator-agent/pom.xml` | Modify | Remover Spring AI si queda sin uso |
| `agents/orchestrator-agent/src/main/resources/application.properties` | Modify | Remover config LLM si queda sin uso |
| `agents/orchestrator-agent/src/main/java/com/genailab/orchestrator/domain/port/RoutingClassifier.java` | Create | Puerto de clasificacion |
| `agents/orchestrator-agent/src/main/java/com/genailab/orchestrator/infrastructure/adapter/routing/RuleBasedRoutingClassifier.java` | Create | Reglas locales |
| `agents/orchestrator-agent/src/main/java/com/genailab/orchestrator/infrastructure/adapter/http/DownstreamAgentClient.java` | Create | Cliente HTTP downstream |
| `agents/orchestrator-agent/src/main/java/com/genailab/orchestrator/infrastructure/adapter/ai/*` | Modify/Delete | Reemplazar tool calling |
| `agents/orchestrator-agent/src/test/**` | Modify/Create | Cubrir clasificacion y adapter |
