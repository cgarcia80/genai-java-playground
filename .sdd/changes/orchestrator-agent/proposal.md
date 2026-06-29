# Proposal: orchestrator-agent

> Updated on 2026-06-29.
> Status: draft (reused open change, awaiting approval before `sdd-apply`)

## Intent

Construir el agente 4 del catalogo: `orchestrator-agent`, un punto de entrada unico que recibe preguntas en lenguaje natural y delega a `doc-query-agent`, `diagnosis-agent` o `smart-search-agent` usando Spring AI tool calling. Inaugura el patron multi-agente: las tools del modelo son HTTP calls a otros microservicios, no metodos locales.

## Scope

- Nuevo servicio Spring Boot 3.x / Java 21 en Docker, expuesto en `:8083`, integrado al `docker-compose.yaml`.
- Endpoint `POST /api/v1/ask` que recibe `{"question": "..."}` y devuelve `{"answer": "...", "routedTo": "<agent-name>|null"}`.
- El modelo de ruteo del orchestrator usa `llama3` via LiteLLM, no `gemini-flash`, porque la pregunta completa puede contener documentacion interna, logs o stacktraces sensibles.
- Tres tools `@Tool` que hacen POST via `RestClient`: `callDocQueryAgent`, `callDiagnosisAgent`, `callSmartSearchAgent`.
- `callDiagnosisAgent` recibe solo el fragmento de log/stacktrace extraido por el modelo como parametro `logContent` y lo mapea al payload `{"log": "..."}`.
- `routedTo` lleva el nombre del agente efectivamente invocado. Si el modelo responde sin invocar tool, `routedTo` es `null`.
- Modo de bypass para desarrollo: header opcional `X-Bypass-Routing` con valores `doc-query`, `diagnosis` o `smart-search`, que saltea el LLM y llama directo al downstream seleccionado. Sirve para probar red/Docker sin gastar requests de tool calling.
- Arquitectura hexagonal consistente con los agentes previos.

## Out of scope

- Composicion/encadenamiento de multiples agentes en una sola pregunta (un request, un agente).
- Friendly fallback si downstream falla; se devuelve error HTTP estandar.
- UI / cliente; solo backend REST.
- Autenticacion de endpoints (queda para change transversal).
- Caching de respuestas o memoria conversacional.
- Reintentos automaticos hacia downstream.
- Timeout configurable por downstream (queda como riesgo conocido para un change posterior).
- Correccion global de la API key Gemini hardcodeada en `litellm_config.yaml`; queda recomendado como change separado de seguridad.

## Affected areas

- Nuevo modulo Maven: `agents/orchestrator-agent/`.
- `docker-compose.yaml`: nuevo service `orchestrator-agent` con `depends_on` de `doc-query-agent`, `diagnosis-agent`, `smart-search-agent` y `litellm`.
- Sin cambios obligatorios en `litellm_config.yaml`; el orchestrator usa el alias local `llama3`.
- Sin cambios en los agentes 1, 2 o 3.

## Capabilities

| Capability | Status | Spec file |
|-----------|--------|-----------|
| OrchestratorAgent | NEW | `.sdd/changes/orchestrator-agent/specs/orchestrator-agent/spec.md` |

## Rollback

`docker compose stop orchestrator-agent` o comentar el service en el compose deshabilita el agente sin afectar al resto del stack. `git revert` del commit revierte codigo y compose. No hay estado persistente, no hay migraciones, no hay cambios de contrato en los agentes downstream.

## Success criteria

- `docker compose up orchestrator-agent` levanta el servicio en `:8083` sin errores.
- `POST /api/v1/ask` con pregunta sobre documentacion rutea a `doc-query-agent` (`routedTo="doc-query-agent"`) y devuelve respuesta coherente.
- `POST /api/v1/ask` con stacktrace o log rutea a `diagnosis-agent` con payload `{"log": "..."}`.
- `POST /api/v1/ask` con pregunta general rutea a `smart-search-agent`.
- `POST /api/v1/ask` con `X-Bypass-Routing` llama directo al downstream indicado y evita la llamada LLM del orchestrator.
- Si un downstream devuelve error o no responde, el orchestrator devuelve HTTP 502 sin romperse.
- `spring.ai.retry.max-attempts=1` configurado.
- Convenciones del stack (hexagonal, constructor injection, modelo por config, tools con `@Tool` + `ThreadLocal`) se cumplen.

## Acknowledged risks

- **Tool calling con modelo local**: `llama3.2:3b` tuvo problemas de tool calling en `smart-search-agent`. Se prioriza privacidad para el orchestrator; si verify demuestra que `llama3` no invoca tools de forma confiable, el change debe bloquear y decidir entre router rule-based local o aceptacion explicita de cloud.
- **Quota Gemini compartida**: aunque el orchestrator usa local, una pregunta ruteada a `smart-search-agent` puede consumir Gemini en el downstream. El bypass reduce el costo de pruebas de red del orchestrator, no elimina el costo del smart-search.
- **Mapping de payload diagnosis-agent**: el modelo debe extraer el log del texto libre. Mitigable con descriptions prescriptivas y smoke tests.
- **Sin timeout granular por downstream**: si un agente queda colgado, el request del orchestrator espera hasta el timeout global del cliente HTTP.

## Recommended Direction

- **Chosen option**: LLM tool calling con tools HTTP, usando `llama3` local para el ruteo del orchestrator y bypass por header para desarrollo.
- **Why**: mantiene el valor didactico multi-agente sin violar la regla de privacidad del repo. El bypass permite validar integracion sin quemar quota ni depender del comportamiento no deterministico del LLM.
- **Trade-off accepted**: el tool calling local puede ser menos confiable que Gemini; verify debe probarlo y bloquear si no cumple.
