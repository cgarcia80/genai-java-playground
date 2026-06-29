# genai-stack — Guía de Uso y Laboratorio de Pruebas

Este proyecto es un laboratorio para experimentar con Inteligencia Artificial Generativa bajo dos enfoques:
1. **Low-Code / Visual**: Usando **Flowise** para armar flujos rápidos de datos y prototipos de agentes.
2. **Código Custom (Pro)**: Usando microservicios **Java 21 / Spring Boot 3.x / Spring AI** con arquitectura hexagonal.

---

## 1. Mapa de Componentes y URLs

Cuando levantás el entorno con `docker compose up`, se exponen los siguientes servicios:

### Dashboards y Herramientas Web (UI)

| Servicio | URL en tu Navegador | Rol | Credenciales / Config |
| :--- | :--- | :--- | :--- |
| **Flowise UI** | [http://localhost:3000](http://localhost:3000) | Orquestación visual low-code para agentes y RAG. | Sin contraseña (desarrollo local). |
| **Qdrant Dashboard** | [http://localhost:6333/dashboard](http://localhost:6333/dashboard) | Consola web de la base de datos vectorial para ver colecciones y vectores. | Sin contraseña. |
| **Langfuse UI** | [http://localhost:3001](http://localhost:3001) | Trazabilidad y observabilidad para monitorear prompts y costos. | Crear cuenta inicial al ingresar. |

### APIs de Infraestructura (Backend)

| Servicio | URL desde Host (Windows) | Hostname Interno (Docker) | Rol |
| :--- | :--- | :--- | :--- |
| **LiteLLM Proxy** | `http://localhost:4000` | `http://litellm:4000` | Proxy OpenAI-compatible (administra modelos cloud y locales). |
| **Ollama** | `http://localhost:11434` | `http://ollama:11434` | Runtime local para correr modelos de LLM y embeddings. |
| **Qdrant API** | `http://localhost:6333` | `http://qdrant:6333` | Vector store (API REST y gRPC). |

### APIs de los Agentes Custom (Java)

| Agente | Puerto | Endpoint Principal | Rol / Patrón |
| :--- | :--- | :--- | :--- |
| **doc-query-agent** | `8080` | `POST /api/v1/query` | RAG sobre documentación local (Ollama). |
| **diagnosis-agent** | `8081` | `POST /api/v1/diagnose` | Diagnóstico estructurado de logs de error (Ollama). |
| **smart-search-agent** | `8082` | `POST /api/v1/chat` | Tool calling / Agentic search (Gemini Cloud). |
| **orchestrator-agent** | `8083` | `POST /api/v1/ask` | Orquestador multi-agente que deriva a los anteriores. |

---

## 2. Guía Práctica: Crear un Flujo RAG en Flowise (Low-Code)

Flowise te permite cargar PDFs y chatear con ellos sin escribir una sola línea de código, usando los mismos modelos de Ollama y la base de datos Qdrant que usan tus agentes Java.

### Paso a paso para armar tu primer flujo de documentos:

1. Abrí [http://localhost:3000](http://localhost:3000) en tu navegador.
2. Hacé clic en **Add New** para crear un chatflow nuevo.
3. Agrega los siguientes nodos usando el menú lateral izquierdo (`+`):
   - **Vector Stores > Qdrant**: Es el nodo que conectará con tu base vectorial.
     - *Connect Database:* `http://qdrant:6333` (Ojo: usá el hostname interno de Docker, no `localhost`).
     - *Collection Name:* Poné un nombre descriptivo, por ejemplo, `flowise-docs`.
   - **Document Loaders > Folder** (o **PDF File**):
     - Arrastralo y conectalo a la entrada `Document` del nodo Qdrant.
     - Cargá el PDF que querés indexar o configuralo para leer de `./docs`.
   - **Embeddings > Ollama Embeddings**:
     - Conectalo a la entrada `Embeddings` del nodo Qdrant.
     - *Base Path:* `http://ollama:11434`.
     - *Model Name:* `nomic-embed-text`.
   - **Chat Models > Ollama Chat**:
     - Conectalo a la entrada `Model` de la cadena conversacional.
     - *Base Path:* `http://ollama:11434`.
     - *Model Name:* `llama3.2:3b`.
   - **Chains > Conversational Retrieval QA Chain**:
     - Este nodo une todo. Conectá el nodo de Qdrant a su entrada `Vector Store Retriever` y el de Ollama Chat a su entrada `Model`.
4. Hacé clic en el ícono de guardar (arriba a la derecha), ponelo en marcha e ingresá al chat de pruebas de Flowise para hacerle preguntas sobre tu documento.
5. Podés monitorear los vectores generados entrando al [Dashboard de Qdrant](http://localhost:6333/dashboard) para ver la nueva colección creada.

---

## 3. Guía de Uso de los Agentes Custom (Java)

Cada agente de Java es un servicio independiente que se expone en su propio puerto. A continuación tenés los comandos para probarlos usando `curl` (en Windows PowerShell, acordate de usar comillas dobles escapadas).

### 3.1 Agente 1: Consulta de Documentación (doc-query-agent :8080)
Este agente implementa RAG en Java con arquitectura hexagonal. Consume la documentación de la carpeta `./docs`.

* **Ingestar Documentación** (debe correrse al menos una vez para cargar los vectores en Qdrant):
  ```bash
  curl -X POST http://localhost:8080/api/v1/ingest
  ```
  *Respuesta:* `{"filesProcessed": 1, "chunksLoaded": 42}`

* **Realizar Consulta RAG**:
  ```bash
  curl -X POST http://localhost:8080/api/v1/query \
    -H "Content-Type: application/json" \
    -d "{\"question\": \"¿Qué es la arquitectura hexagonal?\"}"
  ```

### 3.2 Agente 2: Diagnóstico de Errores (diagnosis-agent :8081)
Mapea un log crudo a una respuesta JSON con estructura rígida (`rootCause`, `location`, `suggestion`).

* **Ejecutar Diagnóstico**:
  ```bash
  curl -X POST http://localhost:8081/api/v1/diagnose \
    -H "Content-Type: application/json" \
    -d "{\"log\": \"java.lang.NullPointerException at com.example.Service.process(Service.java:42)\"}"
  ```

### 3.3 Agente 3: Agente Autónomo con Herramientas (smart-search-agent :8082)
Usa Gemini-Flash para decidir qué herramientas (`getCurrentDate`, `searchDocs`) invocar para responder preguntas libres.

* **Probar Tool Use**:
  ```bash
  curl -X POST http://localhost:8082/api/v1/chat \
    -H "Content-Type: application/json" \
    -d "{\"question\": \"¿Qué fecha es hoy?\"}"
  ```
  *Respuesta:* `{"answer":"Hoy es 29 de junio de 2026.","toolsUsed":["getCurrentDate"]}`

### 3.4 Agente 4: Orquestador General (orchestrator-agent :8083)
Punto de entrada único que usa un **router deterministico por reglas locales** (sin LLM) para derivar al agente adecuado. Las reglas son: si la pregunta contiene palabras como `exception`, `error`, `log`, `stacktrace` → va a `diagnosis-agent`; si contiene `documentación`, `arquitectura`, `proceso`, `entidad` → va a `doc-query-agent`; cualquier otra pregunta → va a `smart-search-agent`. El campo `routedTo` en la respuesta indica a qué agente se derivó.

* **Ejecutar Consulta Orquestada**:
  ```bash
  curl -X POST http://localhost:8083/api/v1/ask \
    -H "Content-Type: application/json" \
    -d "{\"question\": \"Tengo este error: org.postgresql.util.PSQLException: Connection refused\"}"
  ```
  *Respuesta esperada:*
  ```json
  {
    "answer": "El error indica que la aplicación no se pudo conectar con Postgres...",
    "routedTo": "diagnosis-agent"
  }
  ```

* **Bypass directo (útil para debugging)** — el header `X-Bypass-Routing` omite la clasificación y va directo al agente que indiques:
  ```bash
  curl -X POST http://localhost:8083/api/v1/ask \
    -H "Content-Type: application/json" \
    -H "X-Bypass-Routing: doc-query" \
    -d "{\"question\": \"¿Qué es la arquitectura hexagonal?\"}"
  ```
  *Valores válidos:* `doc-query`, `diagnosis`, `smart-search`. Cualquier otro valor devuelve HTTP 400.

---

## 4. Selección Dinámica de Modelos (Ollama vs. OpenAI vs. Gemini)

> **Prerequisito para modelos cloud:** antes de levantar el stack con modelos de OpenAI o Gemini, necesitás un archivo `.env` en la raíz del proyecto con tus API keys:
> ```
> GEMINI_API_KEY=tu-clave-de-google
> OPENAI_API_KEY=sk-proj-...
> ```
> Este archivo está excluido del repositorio por `.gitignore` — nunca subas las claves al repo. Docker Compose lo lee automáticamente al hacer `docker compose up`.

El stack utiliza **LiteLLM** como gateway unificado. Esto significa que los agentes Java nunca le hablan directamente a los servidores de OpenAI, Google o a tu Ollama local; todos envían sus peticiones a `http://localhost:4000`.

### 4.1 Catálogo de Modelos Declarados

En [litellm_config.yaml](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/litellm_config.yaml) se encuentran declarados los alias de los modelos que los agentes pueden solicitar:

* **Modelos Locales (Privacidad Absoluta):**
  - `llama3` (mapeado a `ollama/llama3.2:3b`)
  - `nomic-embed-text` (mapeado a `ollama/nomic-embed-text`)
* **Modelos en la Nube (Velocidad / Agilidad):**
  - `gemini-flash` (mapeado a `gemini/gemini-2.5-flash`, requiere `GEMINI_API_KEY`)
  - `gpt-4o-mini` (mapeado a `openai/gpt-4o-mini`, requiere `OPENAI_API_KEY`)
  - `gpt-4o` (mapeado a `openai/gpt-4o`, requiere `OPENAI_API_KEY`)

### 4.2 ¿Cuándo usar cada modelo?

| Modelo | Cuándo usarlo | Contra |
|--------|--------------|--------|
| `llama3` | Datos sensibles o privados. Sin costo. Sin internet. | Lento en hardware modesto. No soporta tool calling confiablemente. |
| `nomic-embed-text` | Embeddings para RAG. Siempre local. | Solo sirve para embeddings, no para chat. |
| `gemini-flash` | Tool calling, respuestas rápidas, free tier generoso (1000 req/día). | Los datos salen de tu máquina hacia Google. |
| `gpt-4o-mini` | Calidad alta, precio bajo. Buena opción para pruebas cloud. | Los datos salen hacia OpenAI. Tiene costo por token. |
| `gpt-4o` | Máxima capacidad de razonamiento. | Más caro. Reservalo para casos complejos. |

**Regla práctica:**
- ¿Los datos son sensibles o internos? → `llama3` (local)
- ¿Necesitás tool calling confiable? → `gemini-flash`
- ¿Querés calidad cloud sin gastar mucho? → `gpt-4o-mini`

### 4.3 Cómo cambiar el modelo de un Agente — paso a paso completo

Ejemplo concreto: **cambiar `diagnosis-agent` de `llama3` a `gpt-4o-mini`**.

**Paso 1 — Verificar que el alias existe en `litellm_config.yaml`**

```yaml
# Ya debe estar declarado:
- model_name: gpt-4o-mini
  litellm_params:
    model: openai/gpt-4o-mini
    api_key: "os.environ/OPENAI_API_KEY"
```
Si no está, agregalo. Después de modificar este archivo necesitás recrear el contenedor de LiteLLM.

**Paso 2 — Verificar que tenés la API key en `.env`**

```
OPENAI_API_KEY=sk-proj-...
```
Sin esto, LiteLLM va a fallar cuando intente llamar a OpenAI.

**Paso 3 — Actualizar `docker-compose.yaml`**

```yaml
diagnosis-agent:
  environment:
    - SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL=gpt-4o-mini  # ← este valor debe coincidir exactamente con model_name en litellm_config.yaml
```

**Paso 4 — Redesplegar los contenedores afectados**

```bash
# Si modificaste litellm_config.yaml:
docker compose up -d --force-recreate litellm

# Reiniciá el agente:
docker compose up -d --force-recreate diagnosis-agent
```

**Paso 5 — Verificar**

```bash
curl -X POST http://localhost:8081/api/v1/diagnose \
  -H "Content-Type: application/json" \
  -d "{\"log\": \"java.lang.NullPointerException at com.example.Service:42\"}"
```
Si la respuesta tiene `rootCause`, el modelo nuevo está funcionando.

> **Error frecuente:** si el nombre en `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL` no coincide exactamente con el `model_name` en `litellm_config.yaml`, el agente va a fallar con un error de modelo no encontrado. Los nombres son case-sensitive.

### 4.4 Cómo cambiar el modelo de un Agente

Para cambiar el LLM de un agente Java, tenés dos enfoques:

#### Enfoque A: A través de Variables de Entorno en `docker-compose.yaml` (Recomendado)
Pisa la configuración de Spring AI directamente inyectando la variable `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL` en el contenedor del agente dentro de [docker-compose.yaml](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/docker-compose.yaml).

```yaml
  smart-search-agent:
    ...
    environment:
      - SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL=gpt-4o-mini  # <-- Cambiá el alias acá
```
*No requiere volver a compilar el código Java, solo reiniciar el contenedor (`docker compose up -d smart-search-agent`).*

> **Nota:** esta variable aplica a los agentes que usan Spring AI (`doc-query-agent`, `diagnosis-agent`, `smart-search-agent`). El `orchestrator-agent` **no** usa Spring AI — su routing es local y no tiene modelo de LLM propio, así que esta variable no tiene efecto en él.

#### Enfoque B: A través de `application.properties` (En desarrollo local)
Si estás corriendo el agente fuera de Docker de forma local (ej: desde tu IDE), podés editar su archivo `src/main/resources/application.properties` y cambiar la propiedad del modelo:

```properties
spring.ai.openai.chat.options.model=gpt-4o-mini
```
*Requiere empaquetar nuevamente con `mvn clean package` si vas a reconstruir el contenedor Docker.*

---

## 5. Trazabilidad y Monitoreo con Langfuse

Toda la actividad de los modelos cloud de tus agentes queda registrada para auditoría en Langfuse.
1. Entrá a [http://localhost:3001](http://localhost:3001).
2. Registrá un usuario administrador (solo local).
3. Vas a poder ver en tiempo real:
   - Cuántos tokens consume cada agente.
   - El árbol de llamadas del orquestador hacia los agentes secundarios.
   - La latencia exacta de cada interacción.
