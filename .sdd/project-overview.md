# Project Overview: genai-stack

> Generado por `sdd-init` el 2026-06-27.
> Re-ejecutar `sdd-init` para regenerar este archivo. NO se actualiza automáticamente.

## Descripción

Laboratorio personal de agentes de IA para aprender a construir agentes modernos (2026) a mano, sin herramientas low-code. El repo contiene la infraestructura Docker compartida (Ollama, Qdrant, LiteLLM, Langfuse) sobre la cual se van a desplegar agentes especializados construidos en Java/Spring Boot. Cada agente es un servicio REST independiente que le pega al proxy LiteLLM y nunca directo al modelo.

## Stack técnico

- **Infraestructura**: Docker Compose
- **LLM runtime**: Ollama (local, modelo `llama3.2:3b` + embeddings `nomic-embed-text`)
- **Vector store**: Qdrant
- **LLM proxy / gateway**: LiteLLM (activo — Fase 0 completada)
- **Observabilidad**: Langfuse
- **Agentes (a construir)**: Java 21, Spring Boot 3.x, Spring AI, arquitectura hexagonal
- **Build de agentes (a construir)**: Maven

## Arquitectura

El stack sigue un patrón de gateway centralizado: los agentes Java nunca le pegan directo a Ollama sino al proxy LiteLLM, que expone un endpoint estilo OpenAI y decide el modelo de destino por configuración. Qdrant provee almacenamiento vectorial para los agentes con RAG. Langfuse captura trazas de LLM de forma opcional. Cada agente es un microservicio Spring Boot independiente, con su propio endpoint REST y sin estado compartido.

```
Agentes (Java/Spring Boot) → LiteLLM :4000 → Ollama :11434
                                            ↘ Qdrant :6333
```

### Servicios Docker actuales

```
docker-compose.yaml
├── ollama          → :11434  (LLM runtime local)
├── qdrant          → :6333, :6334  (vector store)
├── flowise         → :3000  (low-code UI, referencia — no usada por los agentes)
├── langfuse-db     → postgres (persistencia de Langfuse)
└── langfuse        → :3001  (observabilidad LLM)
```

## Capability Map

### Servicios Docker (infraestructura)

- `ollama` — LLM runtime. Expone API REST compatible con Ollama en `:11434`. Modelos disponibles: `llama3.2:3b`, `nomic-embed-text`.
- `qdrant` — Vector store. REST en `:6333`, gRPC en `:6334`. Colecciones: ninguna aún (se crean por agente).
- `litellm` — Proxy OpenAI-compatible. Expondrá `:4000`. Config: `litellm_config.yaml`. Modelo default: `llama3` via Ollama.
- `langfuse-db` — Postgres 16. Uso exclusivo de Langfuse.
- `langfuse` — Observabilidad de traces LLM. Expone `:3001`.
- `flowise` — Low-code UI. Incluido como referencia. Los agentes construidos en Java NO dependen de Flowise.

### Agentes (catálogo)

| # | Tipo | Nombre | Patrón | Descripción | Privacidad | Estado |
|---|------|---------|--------|-------------|------------|--------|
| 1 | Resolutivo | doc-query-agent | RAG | Consulta de documentación interna via RAG sobre Qdrant | LOCAL — datos sensibles | ✓ COMPLETO |
| 2 | Resolutivo | diagnosis-agent | Prompt directo + respuesta estructurada | Diagnóstico de errores a partir de stack trace o log | LOCAL | ✓ COMPLETO |
| 3 | Agentico | smart-search-agent | Tool use / Function calling | Agente que decide qué herramientas invocar para responder preguntas | LOCAL | EN CONSTRUCCIÓN |
| 4 | Orquestador | orchestrator-agent | Tool use / Multi-agent | Recibe cualquier pregunta y decide a qué agente delegarla (doc-query, diagnosis, smart-search) via HTTP calls como tools | LOCAL | PENDIENTE |

> **Nota (2026-06-28):** `test-gen-agent` fue reemplazado por `smart-search-agent`. La generación de tests queda cubierta por Claude Code CLI, que lo resuelve con más contexto de repo. El agente 3 ahora enseña el patrón de tool use, que es el salto conceptual clave del prompt directo al agente real.
> **Nota (2026-06-29):** `spec-translator-agent` reemplazado por `orchestrator-agent`. El agente 4 enseña el patrón multi-agente: tools que son HTTP calls a los agentes 1, 2 y 3. Un solo endpoint de entrada, el modelo decide la delegación.

## External dependencies

### LiteLLM proxy (pendiente)

- Endpoint objetivo: `http://localhost:4000/v1` (estilo OpenAI)
- Config: `litellm_config.yaml` en repo root
- Modelo default: `ollama/llama3.2:3b` via `http://ollama:11434`

### Ollama (Docker interno)

- Host interno: `http://ollama:11434`
- Host externo (dev): `http://localhost:11434`
- Modelos disponibles: `llama3.2:3b`, `nomic-embed-text`

### Qdrant (Docker interno)

- Host interno: `http://qdrant:6333`
- Host externo (dev): `http://localhost:6333`

## Testing

- **Test runner**: N/A (infraestructura Docker — tests son smoke tests manuales vía `curl`)
- **Unit tests**: N/A en esta fase; aplica cuando se construyan los agentes Java
- **Integration tests**: N/A en esta fase
- **Coverage tools**: N/A
- **Strict TDD mode**: disabled (no hay runner de tests en este repo de infraestructura)

## Notas del análisis

- El repo no contiene código fuente Java aún. Solo `docker-compose.yaml` y directorios de datos de los servicios.
- `sdd-init` fue adaptado para un proyecto de infraestructura Docker (no Java/Maven ni Angular).
- `flowise` está incluido en el compose pero NO forma parte de la arquitectura de agentes — es referencia histórica.
- LiteLLM es el cambio más urgente (Fase 0): sin él, ningún agente puede correr.
- La regla de privacidad ("datos sensibles → modelo local") se implementa en `litellm_config.yaml`, no en el código de los agentes.

## Próximos pasos sugeridos

- **Fase 0**: ✓ completada. LiteLLM activo en Docker.
- **Agente 1**: ✓ completado. `doc-query-agent` operativo en `:8080`.
- **Agente 2**: ✓ completado. `diagnosis-agent` operativo en `:8081`.
- **Agente 3**: `smart-search-agent` — tool use con Spring AI `@Tool`, modelo local, puerto `:8082`.
