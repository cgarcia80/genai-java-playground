# Tasks: orchestrator-agent

> Updated by `sdd-tasks` on 2026-06-29.
> Status: pending (awaiting user approval and `sdd-apply`)
> Tests: incluidos (mantiene la decision del tasks original).

## Implementation order

Phases MUST be executed in order. Within a phase, tasks MUST be executed in listed order. Test tasks MUST NOT be skipped.

## Phase 1: Scaffolding

- [x] **1.1 Crear pom.xml, Dockerfile y clase main**
  - Ref: `agents/orchestrator-agent/pom.xml`, `agents/orchestrator-agent/Dockerfile`, `OrchestratorAgentApplication.java`
  - Implements: D1, D2
  - Done when: Spring Boot 3.4.1, Java 21, Spring AI OpenAI, web y validation declarados; main habilita `AppProperties`.

## Phase 2: Domain

- [x] **2.1 Crear modelos, port, target y excepciones**
  - Ref: `domain/model/*`, `domain/port/OrchestrationPort.java`, `domain/exception/*`
  - Implements: D5, D6, D7
  - Done when: existen `OrchestrationRequest`, `OrchestrationResult`, `RoutingTarget`, `OrchestrationPort`, `DownstreamAgentException`, `InvalidRoutingTargetException`.

- [x] **2.2 Test de parsing de RoutingTarget**
  - Ref: `src/test/.../domain/model/RoutingTargetTest.java`
  - Implements: R-Bypass
  - Done when: valores `doc-query`, `diagnosis`, `smart-search` parsean y valor invalido falla.

## Phase 3: Application

- [x] **3.1 Crear OrchestrateUseCase**
  - Ref: `application/usecase/OrchestrateUseCase.java`
  - Implements: D6
  - Done when: `orchestrate()` delega ruteo LLM y `bypass()` delega invocacion directa.

- [x] **3.2 Test de OrchestrateUseCase**
  - Ref: `src/test/.../application/usecase/OrchestrateUseCaseTest.java`
  - Implements: R-Ruteo, R-Bypass
  - Done when: verifica delegacion normal y bypass.

## Phase 4: Infrastructure config

- [x] **4.1 Crear AppProperties y SpringAiConfig**
  - Ref: `infrastructure/config/AppProperties.java`, `infrastructure/config/SpringAiConfig.java`
  - Implements: D1, D2, D3
  - Done when: tres `RestClient` dedicados y timeouts 5s/90s sin defaults inline. Nota: `ChatClient` queda supercedido por Phase 8.

- [x] **4.2 Crear application.properties**
  - Ref: `src/main/resources/application.properties`
  - Implements: R-Proteccion retry, D1
  - Done when: `server.port=8083`, URLs downstream por env vars y `agents.read-timeout=90s`. Nota: config LLM queda supercedida por Phase 8.

## Phase 5: AI adapter and tools

- [x] **5.1 Crear AgentTools**
  - Ref: `infrastructure/adapter/ai/AgentTools.java`
  - Implements: D3, D4, D5
  - Done when: HTTP POST correcto y respuestas aplanadas. Nota: `@Tool` y `ThreadLocal` quedan supercedidos por Phase 8.

- [x] **5.2 Tests de AgentTools**
  - Ref: `src/test/.../infrastructure/adapter/ai/AgentToolsTest.java`
  - Implements: R-Descripcion tools, R-Fallo downstream
  - Done when: verifica payloads a cada downstream, tracking `routedTo` y exception ante error HTTP.

- [x] **5.3 Crear SpringAiOrchestrationAdapter**
  - Ref: `infrastructure/adapter/ai/SpringAiOrchestrationAdapter.java`
  - Implements: D1, D5, D6
  - Done when: adapter inicial creado. Nota: `ChatClient.tools(...)` queda supercedido por Phase 8.

- [x] **5.4 Tests de SpringAiOrchestrationAdapter**
  - Ref: `src/test/.../infrastructure/adapter/ai/SpringAiOrchestrationAdapterTest.java`
  - Implements: R-Ruteo, R-Campo routedTo, R-Bypass
  - Done when: cubre tool invocada, ninguna tool y bypass directo sin ChatClient.

## Phase 6: REST adapter

- [x] **6.1 Crear DTOs y GlobalExceptionHandler**
  - Ref: `infrastructure/adapter/rest/*`
  - Implements: D7
  - Done when: `@NotBlank question`, error 400 validation, 400 bypass invalido, 502 downstream.

- [x] **6.2 Crear OrchestratorController**
  - Ref: `infrastructure/adapter/rest/OrchestratorController.java`
  - Implements: R-Contrato API, R-Bypass
  - Done when: `POST /api/v1/ask` acepta header opcional `X-Bypass-Routing`.

- [x] **6.3 Tests del controller**
  - Ref: `src/test/.../infrastructure/adapter/rest/OrchestratorControllerTest.java`
  - Implements: R-Contrato API, R-Bypass, R-Fallo downstream
  - Done when: cubre 400 question vacia, 400 bypass invalido, 200 normal, 200 bypass y 502 downstream.

## Phase 7: Docker

- [x] **7.1 Agregar servicio en docker-compose.yaml**
  - Ref: `docker-compose.yaml`
  - Implements: proposal.md affected areas
  - Done when: service `orchestrator-agent` expone `8083:8083`, depende de los tres downstream y define URLs internas.

## Phase 8: Router rule-based rework

- [x] **8.1 Remover dependencia de tool calling del orchestrator**
  - Ref: `agents/orchestrator-agent/pom.xml`, `src/main/resources/application.properties`, `infrastructure/config/SpringAiConfig.java`
  - Implements: D1
  - Done when: el orchestrator no declara Spring AI ni configura modelo LLM propio.

- [x] **8.2 Crear puerto y adapter de clasificacion local**
  - Ref: `domain/port/RoutingClassifier.java`, `infrastructure/adapter/routing/RuleBasedRoutingClassifier.java`
  - Implements: D1, D2, R-Ruteo local deterministico
  - Done when: diagnosis tiene prioridad sobre doc-query y smart-search queda como default.

- [x] **8.3 Tests de clasificacion local**
  - Ref: `src/test/.../infrastructure/adapter/routing/RuleBasedRoutingClassifierTest.java`
  - Implements: R-Ruteo local deterministico
  - Done when: cubre pregunta de docs, stacktrace/error/log y pregunta general.

- [x] **8.4 Reemplazar tools por cliente HTTP downstream**
  - Ref: `infrastructure/adapter/http/DownstreamAgentClient.java`, `infrastructure/adapter/ai/AgentTools.java`
  - Implements: D3, D4, R-Delegacion HTTP
  - Done when: no quedan `@Tool` ni `ThreadLocal`; el cliente HTTP conserva flatten de respuestas.

- [x] **8.5 Reemplazar SpringAiOrchestrationAdapter por adapter rule-based**
  - Ref: `infrastructure/adapter/orchestration/OrchestrationAdapter.java`, `infrastructure/adapter/ai/SpringAiOrchestrationAdapter.java`
  - Implements: D1, D2, D5
  - Done when: `orchestrate()` clasifica localmente y `invoke()` respeta bypass.

- [x] **8.6 Actualizar tests de adapter y controller**
  - Ref: `src/test/.../infrastructure/adapter/orchestration/*Test.java`, `src/test/.../infrastructure/adapter/rest/OrchestratorControllerTest.java`
  - Implements: R-Contrato API, R-Ruteo local deterministico, R-Bypass
  - Done when: no hay mocks de ChatClient y se prueba routeo normal sin LLM.

## Phase 9: Final validation

- [ ] **9.1 Validacion end-to-end contra success criteria**
  - Ref: N/A
  - Implements: proposal.md success criteria
  - Done when: servicio levanta, ruteos rule-based funcionan, bypass funciona y downstream caido devuelve 502.

## References

- Design: `.sdd/changes/orchestrator-agent/design.md`
- Spec: `.sdd/changes/orchestrator-agent/specs/orchestrator-agent/spec.md`
- Proposal: `.sdd/changes/orchestrator-agent/proposal.md`
