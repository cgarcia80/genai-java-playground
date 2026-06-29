# Spec Delta: orchestrator-agent

> Updated by `sdd-spec` on 2026-06-29.

## ADDED Requirements

### Requirement: Contrato de API

The system SHALL expose `POST /api/v1/ask` on port 8083. The request body MUST be `{"question": "string"}`. On success, the response MUST be `{"answer": "string", "routedTo": "<agent-name>|null"}` with HTTP 200.

#### Scenario: pregunta valida
- **GIVEN** `orchestrator-agent` esta en `:8083` y al menos un downstream esta disponible
- **WHEN** `POST /api/v1/ask` recibe `{"question": "Que hace el flujo de pago?"}`
- **THEN** HTTP 200 con `answer` no vacio y `routedTo` con el nombre del agente invocado o `null` si no hubo tool

#### Scenario: question vacia o ausente
- **GIVEN** el servicio esta disponible
- **WHEN** `POST /api/v1/ask` recibe `question` vacio (`""`) o campo ausente
- **THEN** HTTP 400

---

### Requirement: Ruteo via LLM tool calling local

The system SHALL use the LiteLLM alias `llama3` to decide which downstream agent to invoke. The routing model MUST be local because the input question can contain internal documentation, logs, stacktraces or sensitive data. The LLM MUST select at most one downstream agent per request.

#### Scenario: pregunta sobre documentacion
- **GIVEN** los tres agentes downstream estan disponibles
- **WHEN** `POST /api/v1/ask` recibe una pregunta sobre documentacion interna
- **THEN** `routedTo` es `"doc-query-agent"` y `answer` proviene de ese agente

#### Scenario: pregunta con stacktrace o log de error
- **GIVEN** los tres agentes downstream estan disponibles
- **WHEN** `POST /api/v1/ask` recibe una pregunta que contiene un stacktrace, exception o log
- **THEN** `routedTo` es `"diagnosis-agent"` y el log extraido se envia al downstream

#### Scenario: pregunta general
- **GIVEN** los tres agentes downstream estan disponibles
- **WHEN** `POST /api/v1/ask` recibe una pregunta que no refiere a documentacion interna ni a errores
- **THEN** `routedTo` es `"smart-search-agent"`

---

### Requirement: Descripcion de tools

The system SHALL register three tools with the following `@Tool` descriptions, which are the authoritative routing signal for the LLM:

| Tool | Description |
|------|-------------|
| `callDocQueryAgent` | `"Use when the question asks about internal documentation, specific documented features or processes. Forward the full question as the query."` |
| `callDiagnosisAgent` | `"Use when the user provides a stack trace, error log, or exception message, or asks to diagnose an error. Extract only the relevant log, stack trace or error text from the question and pass it as the logContent parameter. Do not include conversational wrapper text."` |
| `callSmartSearchAgent` | `"Use for general questions requiring search, reasoning, or current-date information. Use when the question does not fit documentation lookup or error diagnosis."` |

Each tool MUST forward to its downstream via synchronous HTTP POST using `RestClient`:

- `callDocQueryAgent` -> `POST http://doc-query-agent:8080/api/v1/query` with `{"question": "<query>"}`
- `callDiagnosisAgent` -> `POST http://diagnosis-agent:8081/api/v1/diagnose` with `{"log": "<extracted-log>"}`
- `callSmartSearchAgent` -> `POST http://smart-search-agent:8082/api/v1/chat` with `{"question": "<question>"}`

#### Scenario: delegacion a doc-query-agent
- **GIVEN** `doc-query-agent` responde HTTP 200
- **WHEN** el LLM invoca `callDocQueryAgent`
- **THEN** el orchestrator llama `POST /api/v1/query` del downstream y propaga su `answer`

#### Scenario: delegacion a diagnosis-agent con extraccion de log
- **GIVEN** `diagnosis-agent` esta disponible
- **WHEN** el LLM invoca `callDiagnosisAgent` con `logContent`
- **THEN** el orchestrator llama `POST /api/v1/diagnose` con body `{"log": "<logContent>"}`

---

### Requirement: Campo routedTo

The system SHALL populate `routedTo` with the name of the effectively invoked downstream agent. If the LLM responds without invoking any tool, `routedTo` MUST be `null`.

#### Scenario: una tool invocada
- **GIVEN** el LLM invoca exactamente una tool
- **WHEN** se construye la respuesta
- **THEN** `routedTo` contiene `"doc-query-agent"`, `"diagnosis-agent"` o `"smart-search-agent"`

#### Scenario: ninguna tool invocada
- **GIVEN** el LLM responde directamente sin invocar tools
- **WHEN** se construye la respuesta
- **THEN** `routedTo` es `null`

---

### Requirement: Bypass de ruteo para desarrollo

The system SHALL support an optional HTTP header `X-Bypass-Routing` with values `doc-query`, `diagnosis`, or `smart-search`. When present, the system MUST skip the orchestrator LLM call and invoke the selected downstream directly.

#### Scenario: bypass hacia doc-query
- **GIVEN** `doc-query-agent` esta disponible
- **WHEN** `POST /api/v1/ask` recibe header `X-Bypass-Routing: doc-query`
- **THEN** el orchestrator no llama al LLM, invoca `doc-query-agent`, responde HTTP 200 y `routedTo` es `"doc-query-agent"`

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

### Requirement: Proteccion de retry LLM

The system SHALL configure `spring.ai.retry.max-attempts=1`. The system MUST NOT perform automatic retries when the LLM call fails.

#### Scenario: fallo en llamada al LLM
- **GIVEN** LiteLLM retorna error en el primer intento
- **WHEN** el orchestrator procesa la solicitud sin bypass
- **THEN** el sistema devuelve error al cliente sin reintentar
