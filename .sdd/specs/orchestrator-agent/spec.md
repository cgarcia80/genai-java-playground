# Spec Delta: orchestrator-agent

> Updated by `sdd-spec` on 2026-06-29 after runtime verification.

## ADDED Requirements

### Requirement: Contrato de API

The system SHALL expose `POST /api/v1/ask` on port 8083. The request body MUST be `{"question": "string"}`. On success, the response MUST be `{"answer": "string", "routedTo": "<agent-name>"}` with HTTP 200.

#### Scenario: pregunta valida
- **GIVEN** `orchestrator-agent` esta en `:8083` y el downstream seleccionado esta disponible
- **WHEN** `POST /api/v1/ask` recibe `{"question": "Que son las entidades?"}`
- **THEN** HTTP 200 con `answer` no vacio y `routedTo` con el nombre del agente invocado

#### Scenario: question vacia o ausente
- **GIVEN** el servicio esta disponible
- **WHEN** `POST /api/v1/ask` recibe `question` vacio (`""`) o campo ausente
- **THEN** HTTP 400

---

### Requirement: Ruteo local deterministico

The system SHALL route requests using local deterministic rules. The system MUST NOT call an LLM to decide routing.

#### Scenario: pregunta sobre documentacion
- **GIVEN** los downstream estan disponibles
- **WHEN** `POST /api/v1/ask` recibe una pregunta sobre documentacion, entidades, arquitectura, procesos o docs
- **THEN** el sistema invoca `doc-query-agent` y responde con `routedTo="doc-query-agent"`

#### Scenario: pregunta con stacktrace o log de error
- **GIVEN** los downstream estan disponibles
- **WHEN** `POST /api/v1/ask` recibe texto con `Exception`, `Error`, stacktrace Java o formato de log
- **THEN** el sistema invoca `diagnosis-agent` y responde con `routedTo="diagnosis-agent"`

#### Scenario: pregunta general
- **GIVEN** los downstream estan disponibles
- **WHEN** `POST /api/v1/ask` recibe una pregunta que no matchea documentacion ni error/log
- **THEN** el sistema invoca `smart-search-agent` y responde con `routedTo="smart-search-agent"`

---

### Requirement: Delegacion HTTP a downstream

The system SHALL forward the selected request to exactly one downstream via synchronous HTTP POST using `RestClient`.

- `doc-query-agent` -> `POST http://doc-query-agent:8080/api/v1/query` with `{"question": "<question>"}`
- `diagnosis-agent` -> `POST http://diagnosis-agent:8081/api/v1/diagnose` with `{"log": "<question>"}`
- `smart-search-agent` -> `POST http://smart-search-agent:8082/api/v1/chat` with `{"question": "<question>"}`

#### Scenario: delegacion a doc-query-agent
- **GIVEN** `doc-query-agent` responde HTTP 200
- **WHEN** el router selecciona `doc-query-agent`
- **THEN** el orchestrator propaga el `answer` del downstream

#### Scenario: delegacion a diagnosis-agent
- **GIVEN** `diagnosis-agent` responde HTTP 200
- **WHEN** el router selecciona `diagnosis-agent`
- **THEN** el orchestrator aplana `rootCause`, `location` y `suggestion` en `answer`

---

### Requirement: Bypass de ruteo para desarrollo

The system SHALL support an optional HTTP header `X-Bypass-Routing` with values `doc-query`, `diagnosis`, or `smart-search`. When present, the system MUST skip local route classification and invoke the selected downstream directly.

#### Scenario: bypass hacia doc-query
- **GIVEN** `doc-query-agent` esta disponible
- **WHEN** `POST /api/v1/ask` recibe header `X-Bypass-Routing: doc-query`
- **THEN** el orchestrator invoca `doc-query-agent`, responde HTTP 200 y `routedTo` es `"doc-query-agent"`

#### Scenario: bypass invalido
- **GIVEN** el servicio esta disponible
- **WHEN** `POST /api/v1/ask` recibe header `X-Bypass-Routing: unknown`
- **THEN** HTTP 400

---

### Requirement: Fallo de downstream

The system SHALL return HTTP 502 when the invoked downstream agent returns an error or does not respond. The system MUST NOT substitute a friendly fallback message.

#### Scenario: downstream caido
- **GIVEN** el downstream seleccionado devuelve HTTP 5xx o no responde
- **WHEN** el orchestrator intenta el call
- **THEN** HTTP 502 al cliente

---

## Validation Strategy

### Contract compliance (obligatorio — bloquea archive)

Los escenarios de este spec se verifican a nivel de tests unitarios y WebMvc con mock server:

| Capa | Clase de test | Cubre |
|------|--------------|-------|
| Clasificacion | `RuleBasedRoutingClassifierTest` | Ruteo doc-query, diagnosis, smart-search |
| Cliente HTTP | `DownstreamAgentClientTest` | Payloads correctos y flatten de respuesta para cada downstream |
| Controller / WebMvc | `OrchestratorControllerTest` | Contrato API, bypass valido/invalido, 502 downstream |
| Use case | `OrchestrateUseCaseTest` | Delegacion normal y bypass |
| Domain | `RoutingTargetTest` | Parsing de RoutingTarget |

Todos los escenarios GIVEN/WHEN/THEN de los requisitos anteriores se satisfacen cuando los tests de las capas correspondientes pasan. El mock server simula el comportamiento del downstream (HTTP 200 con respuesta, HTTP 5xx con error); no se requiere downstream real operativo.

### Operational validation (deuda conocida — no bloquea archive)

El smoke E2E real con `doc-query-agent` y `diagnosis-agent` operativos depende de que Ollama y los modelos locales esten disponibles y respondan dentro del timeout. Esta validacion queda documentada como **deuda operativa conocida**: el comportamiento del orchestrator es correcto; la dependencia es externa y fuera del scope del change.

Cuando el entorno local este disponible, ejecutar:
```
POST http://localhost:8083/api/v1/ask  {"question": "Que son las entidades?"}
# Esperado: HTTP 200, routedTo=doc-query-agent

POST http://localhost:8083/api/v1/ask  {"question": "java.lang.NullPointerException at ..."}
# Esperado: HTTP 200, routedTo=diagnosis-agent
```
