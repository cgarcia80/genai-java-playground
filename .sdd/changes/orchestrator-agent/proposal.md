# Proposal: orchestrator-agent

> Updated on 2026-06-29 after runtime verification.
> Status: draft (reused open change, awaiting controlled re-apply)

## Intent

Construir `orchestrator-agent` como punto de entrada unico para delegar preguntas a `doc-query-agent`, `diagnosis-agent` o `smart-search-agent`. Despues de verificar que `llama3` local no responde de forma confiable con tool calling en el router, el ruteo pasa a ser deterministico y local. Los downstream siguen siendo agentes especializados.

## Scope

- Servicio Spring Boot 3.x / Java 21 en Docker, expuesto en `:8083`.
- Endpoint `POST /api/v1/ask` con request `{"question":"..."}` y response `{"answer":"...", "routedTo":"<agent-name>"}`.
- Router local rule-based:
  - preguntas con stacktrace, exception o log -> `diagnosis-agent`
  - preguntas sobre documentacion interna, entidades, arquitectura, procesos o docs -> `doc-query-agent`
  - resto -> `smart-search-agent`
- Tres llamadas HTTP sincronicas via `RestClient` a downstream:
  - `doc-query-agent` -> `/api/v1/query`
  - `diagnosis-agent` -> `/api/v1/diagnose`
  - `smart-search-agent` -> `/api/v1/chat`
- Header opcional `X-Bypass-Routing` (`doc-query`, `diagnosis`, `smart-search`) para forzar el downstream en smoke/dev.
- Timeouts globales: connect `5s`, read `90s`, adecuados para Ollama/LiteLLM local.
- Arquitectura hexagonal consistente con los agentes previos.

## Out of scope

- Tool calling en el orchestrator.
- Clasificacion semantica con LLM cloud.
- Composicion de multiples agentes por request.
- UI, autenticacion, cache, memoria conversacional o retries automaticos.
- Cambios en los agentes downstream.
- Correccion de la API key Gemini hardcodeada en `litellm_config.yaml`; queda como deuda separada.

## Affected areas

- `agents/orchestrator-agent/`: ajustar adapter de orquestacion para usar router rule-based y cliente HTTP local.
- `docker-compose.yaml`: service `orchestrator-agent` ya agregado.
- Sin cambios requeridos en `litellm_config.yaml`; el orchestrator no consume LLM.

## Success criteria

- `orchestrator-agent` levanta en `:8083`.
- Pregunta sobre documentacion rutea a `doc-query-agent` y devuelve `routedTo="doc-query-agent"`.
- Pregunta con stacktrace/log rutea a `diagnosis-agent`.
- Pregunta general rutea a `smart-search-agent`.
- `X-Bypass-Routing` llama directo al downstream indicado.
- Header bypass invalido devuelve HTTP 400.
- Downstream caido o con error devuelve HTTP 502.
- Tests unitarios/WebMvc pasan.
- Smoke E2E demuestra al menos `doc-query` y `smart-search` con HTTP 200.

## Acknowledged risks

- El router rule-based es menos flexible que tool calling; nuevas categorias requieren actualizar reglas.
- `diagnosis-agent` y `doc-query-agent` pueden tardar por Ollama local; por eso el timeout de lectura es 90s.
- `smart-search-agent` sigue usando Gemini y puede consumir quota, pero eso ocurre en el downstream, no en el router.

## Recommended Direction

- **Chosen option**: router rule-based local + HTTP delegation.
- **Why**: respeta privacidad, evita cuelgues de tool calling local, elimina dependencia de Gemini en el router y permite smoke reproducible.
- **Trade-off accepted**: el enrutamiento es mas simple y explicito; no intenta interpretar intencion compleja con LLM.
