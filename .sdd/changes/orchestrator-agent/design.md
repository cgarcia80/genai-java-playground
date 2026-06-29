# Design: orchestrator-agent

> Updated by `sdd-design` on 2026-06-29.

## Decisions

### D1: Modelo de ruteo

**Decision**: el orchestrator usa `llama3` via LiteLLM para tool calling.

**Rationale**: la pregunta completa puede contener documentacion interna, logs o stacktraces. Por convencion del repo, datos sensibles no deben salir a cloud.

**Alternatives considered**:
- `gemini-flash`: descartado para el router porque ve el input completo antes de delegar.
- Router rule-based: descartado para este change porque el objetivo didactico es multi-agent tool calling.

**Consequences**: `application.properties` usa `spring.ai.openai.chat.options.model=llama3`. Verify debe bloquear si el tool calling local no funciona.

### D2: Configuracion downstream

**Decision**: usar `AppProperties` con `@ConfigurationProperties(prefix="agents")` para URLs y timeouts globales (`connectTimeout=5s`, `readTimeout=90s` en local).

**Rationale**: tres URLs + timeouts forman una agrupacion coherente. No se permiten defaults inline; faltantes deben romper el arranque. El lab local con Ollama/LiteLLM puede tardar mas de 30s por request; 90s evita falsos 502 durante smoke sin ocultar cuelgues largos.

**Alternatives considered**:
- `@Value` campo a campo: mas alineado al baseline, pero dispersa el contrato.

**Consequences**: `AppProperties` contiene `docQueryAgent.url`, `diagnosisAgent.url`, `smartSearchAgent.url`, `connectTimeout`, `readTimeout`. `application.properties` declara `agents.read-timeout=90s`.

### D3: RestClient por downstream

**Decision**: tres beans `RestClient` dedicados.

**Rationale**: cada tool inyecta su cliente y no pasa base URLs por llamada.

**Alternatives considered**:
- Un cliente global: descartado por menor claridad y peor testabilidad.

**Consequences**: `SpringAiConfig` crea `docQueryClient`, `diagnosisClient`, `smartSearchClient`.

### D4: Respuesta downstream aplanada

**Decision**: cada tool retorna un `String` limpio al modelo.

**Rationale**: el contrato publico del orchestrator es `{answer, routedTo}`; metadata como `sources` o `toolsUsed` no forma parte del response.

**Alternatives considered**:
- Pasar JSON raw al modelo: descartado por ruido y tokens.

**Consequences**: `AgentTools` extrae `answer`, o concatena `rootCause`, `location` y `suggestion` para diagnosis.

### D5: `routedTo` sin tool

**Decision**: si `ThreadLocal` queda vacio, `routedTo = null`.

**Rationale**: cumple el spec y evita strings magicos como `"none"`.

**Alternatives considered**:
- `"none"`: descartado por inconsistencia contractual.

**Consequences**: `OrchestrationResult.routedTo()` admite `null`.

### D6: Bypass por header

**Decision**: `X-Bypass-Routing` se procesa en el controller y llama a un metodo explicito de bypass en el use case.

**Rationale**: permite validar red/Docker sin gastar quota ni depender del LLM. Al estar en controller, queda visible como comportamiento HTTP de desarrollo.

**Alternatives considered**:
- No agregar bypass: descartado porque encarece verify y debugging.

**Consequences**: valores validos `doc-query`, `diagnosis`, `smart-search`; valor invalido -> 400.

### D7: Fallos downstream

**Decision**: `DownstreamAgentException` se mapea a HTTP 502.

**Rationale**: expresa que fallo un upstream del orchestrator y evita fallback falso.

**Alternatives considered**:
- 503/504 diferenciados: descartado para MVP.

**Consequences**: `GlobalExceptionHandler` maneja 400 validation, 400 bypass invalido y 502 downstream.

## Component shapes

### OrchestratorController
- **Location**: `infrastructure/adapter/rest`
- **Public methods**:
  - `ResponseEntity<AskResponseDto> ask(AskRequestDto request, String bypassRouting)`
- **Dependencies**: `OrchestrateUseCase`.

### OrchestrateUseCase
- **Location**: `application/usecase`
- **Public methods**:
  - `OrchestrationResult orchestrate(OrchestrationRequest request)`
  - `OrchestrationResult bypass(OrchestrationRequest request, RoutingTarget target)`
- **Dependencies**: `OrchestrationPort`.

### OrchestrationPort
- **Location**: `domain/port`
- **Public methods**:
  - `OrchestrationResult orchestrate(OrchestrationRequest request)`
  - `OrchestrationResult invoke(OrchestrationRequest request, RoutingTarget target)`

### AgentTools
- **Location**: `infrastructure/adapter/ai`
- **Public methods**:
  - `String callDocQueryAgent(String query)`
  - `String callDiagnosisAgent(String logContent)`
  - `String callSmartSearchAgent(String question)`
  - `void clearRoutedTo()`
  - `List<String> getRoutedTo()`
- **Dependencies**: tres `RestClient`.

### SpringAiOrchestrationAdapter
- **Location**: `infrastructure/adapter/ai`
- **Extends / Implements**: `OrchestrationPort`
- **Dependencies**: `ChatClient`, `AgentTools`.

## Cross-cutting concerns

- **Transactionality**: N/A, no hay persistencia.
- **Error handling**: validation -> 400, bypass invalido -> 400, downstream -> 502, LLM error sin retry.
- **Idempotency**: N/A, requests sin estado.
- **Observability**: loguear longitud de pregunta, target elegido y downstream status; no loguear pregunta completa, respuesta ni logContent.
- **Backward compatibility**: cambio aditivo; no modifica agentes existentes.
- **Security**: router local por privacidad. API key Gemini hardcodeada queda como deuda separada.

## File changes

| File | Action | Reason |
|------|--------|--------|
| `agents/orchestrator-agent/**` | Create | Nuevo agente Spring Boot |
| `docker-compose.yaml` | Modify | Agregar service `orchestrator-agent` |
