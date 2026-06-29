# genai-stack — Guía de uso

## Arquitectura

Los agentes Java nunca le hablan directo a Ollama — siempre pasan por LiteLLM (proxy estilo OpenAI).

```
[cliente]  →  Agente :808x  →  LiteLLM :4000  →  Ollama :11434
                           ↘  Qdrant :6333
```

| Servicio       | Puerto | Rol                          |
|----------------|--------|------------------------------|
| LiteLLM        | 4000   | Proxy LLM (OpenAI-compatible)|
| Ollama         | 11434  | LLM runtime local            |
| Qdrant         | 6333   | Vector store                 |
| Langfuse       | 3001   | Observabilidad de trazas     |
| doc-query-agent| 8080   | Agente RAG                   |
| diagnosis-agent| 8081   | Agente de diagnóstico        |
| smart-search-agent | 8082 | Agente con tool use        |

---

## Verificar estado de los containers

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Si alguno está caído:

```bash
docker compose up -d <nombre-del-servicio>
```

---

## Agente 1 — doc-query-agent (:8080)

**Patrón:** RAG (Retrieval-Augmented Generation) sobre Qdrant.  
**Modelo:** Ollama local (`llama3.2:3b`).  
**Documento disponible:** `docs/Elementos de Arquitectura y Diseño (1.0.0).pdf`

### Paso 1 — Ingestar el documento

Necesita ejecutarse al menos una vez (o cuando cambie el contenido de `./docs`).

```bash
curl -X POST http://localhost:8080/api/v1/ingest
```

Respuesta esperada:
```json
{"filesProcessed": 1, "chunksLoaded": 42}
```

### Paso 2 — Consultar

```bash
curl -X POST http://localhost:8080/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{"question": "¿Qué es la arquitectura hexagonal?"}'
```

Respuesta:
```json
{
  "answer": "...",
  "sources": [
    { "file": "Elementos de Arquitectura...", "snippet": "..." }
  ]
}
```

El campo `sources` indica de qué parte del documento vino cada respuesta.

---

## Agente 2 — diagnosis-agent (:8081)

**Patrón:** Prompt directo con respuesta estructurada.  
**Modelo:** Ollama local (`llama3.2:3b`).  
**Uso:** Diagnóstico de errores a partir de un log o stack trace.

```bash
curl -X POST http://localhost:8081/api/v1/diagnose \
  -H "Content-Type: application/json" \
  -d '{"log": "java.lang.NullPointerException at com.example.Service.process(Service.java:42)"}'
```

Respuesta:
```json
{
  "rootCause": "...",
  "location": "com.example.Service.process(Service.java:42)",
  "suggestion": "..."
}
```

---

## Agente 3 — smart-search-agent (:8082)

**Patrón:** Tool use / Function calling.  
**Modelo:** Gemini 2.5-flash (cloud) — el modelo local no soporta tool calling en formato OpenAI.  
**Limite:** Free tier de Gemini = 20 requests/día. Cada request con tool use consume 2 créditos (2 roundtrips).

El agente decide autónomamente qué tool invocar según la pregunta.

### Tools disponibles

| Tool | Descripción |
|------|-------------|
| `getCurrentDate` | Devuelve la fecha actual en formato ISO-8601 |
| `searchDocs` | Busca en Qdrant (requiere ingest previo en el agente 1) |

### Verificar getCurrentDate

```bash
curl -X POST http://localhost:8082/api/v1/chat -H "Content-Type: application/json" -d "{\"question\":\"Que fecha es hoy?\"}"
```

Respuesta real (2026-06-29):
```json
{"answer":"Hoy es 29 de junio de 2026.","toolsUsed":["getCurrentDate"]}
```

### Verificar searchDocs (requiere ingest previo en agente 1)

```bash
curl -X POST http://localhost:8082/api/v1/chat -H "Content-Type: application/json" -d "{\"question\":\"Que patrones arquitectonicos menciona el documento?\"}"
```

Respuesta real (2026-06-29):
```json
{
  "answer": "El documento menciona los siguientes elementos de arquitectura y diseño:\n\n* **Dominio del sistema**: Se refiere a todo el conocimiento relacionado con el sistema...\n* **Value Object**: Objetos que son idénticos si sus valores son iguales...\n* **Business Objects (BO)**: Forma de modelar los procesos y tareas dentro del sistema...",
  "toolsUsed": ["searchDocs"]
}
```

El campo `toolsUsed` muestra qué tools invocó el modelo para responder.

> **Nota de rendimiento:** El agente 3 responde en ~3 segundos porque usa Gemini cloud. El agente 1 tarda ~3 minutos porque usa llama3.2:3b en CPU local. El trade-off es intencional: documentos sensibles quedan en el modelo local.

> **En Windows**, usar comillas dobles escapadas en el `-d`. Las comillas simples no funcionan en PowerShell/cmd.

---

## Flujo completo de prueba (orden recomendado)

```bash
# 1. Verificar containers
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 2. Ingestar documentos (una sola vez)
curl -X POST http://localhost:8080/api/v1/ingest

# 3. Probar RAG
curl -X POST http://localhost:8080/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{"question": "¿Qué es un puerto en arquitectura hexagonal?"}'

# 4. Probar diagnóstico
curl -X POST http://localhost:8081/api/v1/diagnose \
  -H "Content-Type: application/json" \
  -d '{"log": "ERROR org.springframework.dao.DataIntegrityViolationException: could not execute statement"}'

# 5. Probar tool use
curl -X POST http://localhost:8082/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "¿Qué fecha es hoy?"}'
```
