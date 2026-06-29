# Spec: smart-search-agent

> Creado: 2026-06-28. Patrón: Tool use / Function calling.

## Propósito

Agente que recibe una pregunta en lenguaje natural y decide autónomamente qué herramientas invocar para responderla. El LLM controla el flujo — no el código.

## Patrón que enseña

Tool use / Function calling. El LLM recibe la pregunta, evalúa qué herramientas tiene disponibles, decide cuáles llamar (y en qué orden), procesa los resultados y construye la respuesta final. El desarrollador define las herramientas; el modelo decide cuándo usarlas.

## Endpoint

```
POST /api/v1/chat
Content-Type: application/json

{ "question": "string" }
```

```
HTTP 200
{
  "answer": "string",
  "toolsUsed": ["searchDocs", "getCurrentDate"]  // para observabilidad
}
```

## Herramientas disponibles

| Tool | Firma | Descripción |
|------|-------|-------------|
| `searchDocs` | `searchDocs(query: String): String` | Busca en Qdrant (colección `doc-query-docs`) y devuelve los chunks más relevantes como texto |
| `getCurrentDate` | `getCurrentDate(): String` | Devuelve la fecha actual en formato ISO-8601 |

## Requisitos

| ID | Requisito |
|----|-----------|
| R1 | `POST /api/v1/chat` acepta `{"question": "..."}` y devuelve HTTP 200 con `answer` y `toolsUsed` |
| R2 | `question` vacío o ausente devuelve HTTP 400 con mensaje de error |
| R3 | El agente invoca `searchDocs` cuando la pregunta refiere a contenido de documentación |
| R4 | El agente invoca `getCurrentDate` cuando la pregunta refiere a la fecha actual |
| R5 | El agente puede invocar ambas herramientas en una misma respuesta si la pregunta lo requiere |
| R6 | El agente puede responder sin invocar ninguna herramienta si la pregunta no lo requiere |
| R7 | `toolsUsed` lista los nombres de las herramientas efectivamente invocadas (vacío si ninguna) |
| R8 | Las herramientas se implementan con la anotación `@Tool` de Spring AI |
| R9 | Arquitectura hexagonal: dominio, puerto, caso de uso, adapter REST, adapter AI |
| R10 | Modelo local vía LiteLLM — ningún dato sale de la red Docker |

## Stack

- Java 21, Spring Boot 3.4.1, Spring AI 1.0.0
- `spring-ai-starter-model-openai` (apunta a LiteLLM)
- `spring-ai-starter-vector-store-qdrant` (para `searchDocs`)
- Puerto: `8082`

## Decisiones de diseño

- Las herramientas se definen en una clase `SearchTools` anotada con `@Component`. Spring AI las registra automáticamente en el `ChatClient` via `.tools(searchTools)`.
- `searchDocs` reutiliza el `VectorStore` de Qdrant configurado con la colección `doc-query-docs` — misma colección que `doc-query-agent`.
- `toolsUsed` se construye en el adapter AI capturando qué métodos invocó Spring AI durante la llamada.

## Notas

- `llama3.2:3b` soporta tool calling básico. En preguntas ambiguas puede elegir la herramienta incorrecta — comportamiento esperado y parte del aprendizaje.
- Si el modelo falla en tool calling reiteradamente, considerar cambiar a un modelo con mejor soporte (ej: `qwen2.5:7b` via Ollama).
