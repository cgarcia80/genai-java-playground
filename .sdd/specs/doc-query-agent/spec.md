# Spec: doc-query-agent

> Generated inline on 2026-06-27. Change: agente-1-doc-query.

## Requerimientos funcionales

### Ingesta

- R1: El agente MUST exponer `POST /api/v1/ingest` que procesa todos los archivos en `/app/docs`.
- R2: El agente MUST soportar PDF (`.pdf`) y Markdown (`.md`, `.markdown`).
- R3: El agente MUST chunkear los documentos con `TokenTextSplitter`, tamaño 512 tokens, overlap 50 tokens.
- R4: El agente MUST generar embeddings via LiteLLM (`nomic-embed-text`) y persistirlos en Qdrant (colección `doc-query-docs`).
- R5: El endpoint de ingesta MUST ser **full-refresh**: borra la colección existente y la recarga desde cero. Garantiza idempotencia.
- R6: El endpoint de ingesta MUST devolver `{ "chunks_loaded": <int>, "files_processed": <int> }` con HTTP 200.
- R7: Si `/app/docs` está vacío, el agente MUST devolver `{ "chunks_loaded": 0, "files_processed": 0 }` con HTTP 200 (no es error).
- R8: Archivos con extensión no soportada SHOULD ser ignorados con log de warning (no falla la ingesta).

### Consulta

- R9: El agente MUST exponer `POST /api/v1/query` con body `{ "question": "string" }`.
- R10: El agente MUST buscar los top-5 chunks más relevantes en Qdrant usando similarity search.
- R11: El agente MUST construir un prompt con el contexto recuperado y llamar a LiteLLM (alias `llama3`).
- R12: El agente MUST devolver `{ "answer": "string", "sources": [{ "file": "string", "snippet": "string" }] }` con HTTP 200.
- R13: Si no se encuentran chunks relevantes (score < umbral o colección vacía), el agente MUST devolver answer: `"No encontré información relevante en la documentación cargada."` con sources vacío.
- R14: El campo `question` es obligatorio. Si está ausente o vacío, devolver HTTP 400.

### No funcionales

- R15: El agente MUST usar modelo LOCAL siempre. Sin tráfico a APIs cloud.
- R16: El agente MUST seguir arquitectura hexagonal. El domain no importa clases de Spring ni Spring AI.
- R17: El agente SHOULD loguear el nombre del archivo procesado durante ingesta y el score de los chunks recuperados durante consulta.

## Decisiones sobre puntos abiertos

| Punto | Decisión |
|-------|----------|
| Chunking | `TokenTextSplitter`: 512 tokens, overlap 50 |
| Política de ingesta | Full-refresh: borra colección y recarga. Simple e idempotente. |
| "No sé" | Answer fija: "No encontré información relevante en la documentación cargada." Sources: `[]` |
| Estructura de sources | `{ "file": "<nombre-archivo>", "snippet": "<primeros 200 chars del chunk>" }` |
| HTTP errors | 400 para input inválido, 503 si LiteLLM/Qdrant no responden, 500 para errores inesperados |

## Contratos de API

### POST /api/v1/ingest

**Request**: sin body (procesa todo `/app/docs`)

**Response 200**:
```json
{
  "files_processed": 3,
  "chunks_loaded": 142
}
```

**Response 503** (LiteLLM o Qdrant no disponibles):
```json
{
  "error": "SERVICE_UNAVAILABLE",
  "message": "No se pudo conectar con el servicio de embeddings o vector store."
}
```

### POST /api/v1/query

**Request**:
```json
{
  "question": "¿Qué hace el flujo de pago?"
}
```

**Response 200**:
```json
{
  "answer": "El flujo de pago consiste en...",
  "sources": [
    { "file": "analisis-funcional-pago.pdf", "snippet": "El flujo de pago inicia cuando..." },
    { "file": "manual-integracion.md", "snippet": "La integración con el gateway..." }
  ]
}
```

**Response 400** (question ausente o vacía):
```json
{
  "error": "BAD_REQUEST",
  "message": "El campo 'question' es requerido y no puede estar vacío."
}
```

## Escenarios

### Ingesta

**Escenario 1: ingesta exitosa**
```
Given: hay 2 archivos en /app/docs (un PDF y un MD)
  And: LiteLLM responde a POST /v1/embeddings
  And: Qdrant está disponible en :6334
When: POST /api/v1/ingest
Then: HTTP 200
  And: files_processed = 2
  And: chunks_loaded > 0
  And: la colección doc-query-docs existe en Qdrant con esos chunks
```

**Escenario 2: directorio vacío**
```
Given: /app/docs no tiene archivos (o solo tiene .gitkeep)
When: POST /api/v1/ingest
Then: HTTP 200
  And: files_processed = 0, chunks_loaded = 0
```

**Escenario 3: reingesta (idempotencia)**
```
Given: ya existe la colección doc-query-docs con 50 chunks de una ingesta anterior
  And: los mismos archivos están en /app/docs
When: POST /api/v1/ingest (segunda vez)
Then: HTTP 200
  And: la colección tiene exactamente los mismos chunks (no duplicados)
```

### Consulta

**Escenario 4: pregunta con contexto relevante**
```
Given: doc-query-docs tiene chunks del análisis funcional
When: POST /api/v1/query { "question": "¿Qué hace el flujo de pago?" }
Then: HTTP 200
  And: answer es una respuesta coherente basada en el contenido de los docs
  And: sources contiene al menos 1 elemento con file y snippet no vacíos
```

**Escenario 5: pregunta sin contexto relevante**
```
Given: doc-query-docs tiene chunks sobre pagos
When: POST /api/v1/query { "question": "¿Cuántas lunas tiene Júpiter?" }
Then: HTTP 200
  And: answer = "No encontré información relevante en la documentación cargada."
  And: sources = []
```

**Escenario 6: question vacía**
```
Given: cualquier estado de la colección
When: POST /api/v1/query { "question": "" }
Then: HTTP 400
  And: error = "BAD_REQUEST"
```
