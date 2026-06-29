# Guía Técnica: Cómo funciona este stack de IA

> Para alguien nuevo en el mundo de la IA generativa y Spring AI. No asume conocimientos previos sobre LLMs, RAG, ni arquitectura hexagonal.

---

## 1. El panorama completo

Este proyecto es un laboratorio con cuatro microservicios Java que usan modelos de lenguaje (LLMs) para tareas distintas: buscar en documentación, diagnosticar errores, hacer búsquedas libres y orquestar todo eso desde un punto de entrada único.

```
Tu request HTTP
      │
      ▼
orchestrator-agent (:8083)   ← punto de entrada único
      │
      ├─► doc-query-agent (:8080)    ← RAG sobre documentos
      ├─► diagnosis-agent (:8081)    ← diagnóstico de logs
      └─► smart-search-agent (:8082) ← tool calling / búsqueda
              │
              ▼
         LiteLLM (:4000)   ← proxy que unifica todos los modelos
              │
              ├─► Ollama (local: llama3, nomic-embed-text)
              ├─► Google Gemini (cloud: gemini-flash)
              └─► OpenAI (cloud: gpt-4o-mini, gpt-4o)
```

Los agentes Java nunca hablan directamente con OpenAI o Google. Siempre hablan con LiteLLM, que hace de intermediario.

---

## 2. Qué es LiteLLM y por qué existe

LiteLLM es un servidor proxy escrito en Python que expone una API unificada compatible con el formato de OpenAI, sin importar qué modelo hay "detrás".

**El problema que resuelve:** OpenAI, Google, Anthropic y Ollama tienen APIs distintas. Si escribís código para OpenAI y después querés cambiar a Gemini, tenés que reescribir el cliente HTTP. Con LiteLLM en el medio, tu código Java siempre llama al mismo endpoint (`http://litellm:4000`) con el mismo formato. Solo cambiás el nombre del modelo.

**Configuración:** `litellm_config.yaml` en la raíz del proyecto declara todos los modelos disponibles como alias:

```yaml
- model_name: gpt-4o-mini          # nombre que pide Java
  litellm_params:
    model: openai/gpt-4o-mini       # modelo real detrás
    api_key: "os.environ/OPENAI_API_KEY"
```

---

## 3. Qué es Spring AI

Spring AI es una librería de Spring Boot que hace para los LLMs lo mismo que Spring Data hace para las bases de datos: te da una abstracción para no atarte a un proveedor.

Con Spring AI podés hacer esto en Java:

```java
// Le preguntás algo al LLM con una sola línea
String respuesta = chatClient.prompt()
    .user("¿Qué es la arquitectura hexagonal?")
    .call()
    .content();
```

Internamente Spring AI construye el request HTTP, maneja el streaming, parsea la respuesta y te devuelve el texto. Vos no ves nada de eso.

**Por qué apunta a LiteLLM y no a OpenAI directamente:** en `application.properties` de cada agente hay algo así:

```properties
spring.ai.openai.base-url=http://litellm:4000
spring.ai.openai.api-key=no-needed
spring.ai.openai.chat.options.model=gpt-4o-mini
```

Le decimos a Spring AI "hablá con este servidor en el puerto 4000 usando el protocolo de OpenAI". LiteLLM recibe la request y la redirige al modelo real. Elegante.

---

## 4. Arquitectura Hexagonal: por qué el código está dividido así

La arquitectura hexagonal (también llamada Ports & Adapters) organiza el código en tres capas con una regla de oro: **las capas internas no conocen a las externas**.

```
┌─────────────────────────────────────────┐
│  infrastructure/  (frameworks, HTTP, DB) │
│  ┌───────────────────────────────────┐  │
│  │  application/  (casos de uso)     │  │
│  │  ┌─────────────────────────────┐  │  │
│  │  │  domain/  (lógica pura)     │  │  │
│  │  │  (sin Spring, sin HTTP)     │  │  │
│  │  └─────────────────────────────┘  │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

**`domain/`** — los modelos y puertos de la aplicación. No importa ningún framework. Es puro Java:
- `OrchestrationRequest` — lo que llega del usuario
- `OrchestrationResult` — lo que devuelve el sistema
- `RoutingTarget` — enum: DOC_QUERY, DIAGNOSIS, SMART_SEARCH
- `OrchestrationPort` — *interfaz* que define qué puede hacer el sistema (sin implementar cómo)

**`application/`** — los casos de uso. Orquesta el dominio, no sabe cómo funciona la infraestructura:
- `OrchestrateUseCase` — recibe una pregunta y delega al puerto

**`infrastructure/`** — los adaptadores concretos. Aquí viven Spring Boot, HTTP, LiteLLM:
- `OrchestratorController` — recibe el HTTP request
- `RuleBasedRoutingClassifier` — implementa la clasificación por reglas
- `DownstreamAgentClient` — hace HTTP calls a los otros agentes
- `OrchestrationAdapter` — implementa `OrchestrationPort` usando los dos anteriores

**El beneficio práctico:** si mañana querés cambiar la clasificación de reglas locales a un LLM, solo reemplazás `RuleBasedRoutingClassifier` por otra implementación. `OrchestrateUseCase` y `OrchestrationPort` no se tocan.

---

## 5. Tour por el código del orchestrator-agent

Vamos de afuera hacia adentro: desde que llega el HTTP request hasta que se decide a qué agente mandar la pregunta.

### 5.1 El Controller (entrada HTTP)

**Archivo:** `infrastructure/adapter/rest/OrchestratorController.java`

Cuando hacés `POST /api/v1/ask`, Spring Boot llama a este método. El controller:
1. Recibe el JSON `{"question": "..."}` como `AskRequestDto`
2. Lee el header opcional `X-Bypass-Routing`
3. Llama al use case y devuelve `{"answer": "...", "routedTo": "..."}`

Si viene el header de bypass, saltea la clasificación y va directo al agente pedido. Si no viene, usa la clasificación automática.

### 5.2 El Use Case (lógica de aplicación)

**Archivo:** `application/usecase/OrchestrateUseCase.java`

```java
public OrchestrationResult orchestrate(OrchestrationRequest request) {
    return port.orchestrate(request);  // delega al puerto
}

public OrchestrationResult bypass(OrchestrationRequest request, RoutingTarget target) {
    return port.invoke(request, target);  // va directo al target
}
```

Es deliberadamente simple. Solo delega. La lógica real está en los adaptadores.

### 5.3 El Clasificador (router deterministico)

**Archivo:** `infrastructure/adapter/routing/RuleBasedRoutingClassifier.java`

```java
public RoutingTarget classify(OrchestrationRequest request) {
    String question = normalized(request.question());  // minúsculas
    if (isDiagnosis(question)) return RoutingTarget.DIAGNOSIS;
    if (isDocQuery(question)) return RoutingTarget.DOC_QUERY;
    return RoutingTarget.SMART_SEARCH;  // default
}
```

Prioridad: **diagnosis > doc-query > smart-search**.

`isDiagnosis()` busca palabras como `exception`, `error`, `failed`, o un patrón de stacktrace Java (`at com.example.Service.java:42`).

`isDocQuery()` busca `documentación`, `arquitectura`, `entidad`, `dominio`, etc.

**Por qué no usar un LLM para clasificar?** Porque sería lento (180ms+), gastaría tokens, y para este caso las reglas son suficientemente precisas. El LLM se usa donde agrega valor real: generando respuestas, no tomando decisiones binarias simples.

### 5.4 El Cliente HTTP downstream

**Archivo:** `infrastructure/adapter/http/DownstreamAgentClient.java`

Tiene tres métodos, uno por agente. Cada uno sabe qué endpoint llamar y qué formato de request/response usar:

```java
// Para doc-query: manda question, espera {answer: "..."}
public String callDocQueryAgent(String question) { ... }

// Para diagnosis: manda log, espera {rootCause, location, suggestion}
// y aplana la respuesta a un String
public String callDiagnosisAgent(String logContent) { ... }

// Para smart-search: manda question, espera {answer: "..."}
public String callSmartSearchAgent(String question) { ... }
```

Si el downstream no responde o devuelve error, lanza `DownstreamAgentException`, que el `GlobalExceptionHandler` convierte automáticamente en HTTP 502.

### 5.5 El flujo completo en un diagrama

```
POST /api/v1/ask {"question": "Tengo un NullPointerException"}
         │
         ▼
OrchestratorController
         │ crea OrchestrationRequest
         ▼
OrchestrateUseCase.orchestrate()
         │ delega a OrchestrationPort
         ▼
OrchestrationAdapter.orchestrate()
         │ llama a RuleBasedRoutingClassifier
         ▼
RuleBasedRoutingClassifier.classify()
         │ detecta "nullpointerexception" → DIAGNOSIS
         ▼
OrchestrationAdapter.invoke(request, DIAGNOSIS)
         │ switch(DIAGNOSIS) → callDiagnosisAgent()
         ▼
DownstreamAgentClient → POST http://diagnosis-agent:8081/api/v1/diagnose
         │ recibe {rootCause, location, suggestion}
         │ aplana a String
         ▼
OrchestrationResult("Root cause: ...", "diagnosis-agent")
         │
         ▼
HTTP 200 {"answer": "Root cause: ...", "routedTo": "diagnosis-agent"}
```

---

## 6. Cómo funciona el RAG en doc-query-agent

RAG (Retrieval-Augmented Generation) es la técnica de "buscar documentos relevantes y pasarlos al LLM como contexto".

**El problema que resuelve:** los LLMs no conocen tu documentación interna. Si le preguntás a GPT-4 sobre los procesos de tu empresa, no sabe nada. RAG resuelve esto: primero busca los fragmentos relevantes de tus docs, y se los "muestra" al LLM antes de pedirle que responda.

### Paso 1: Ingestión (se hace una sola vez)

`POST /api/v1/ingest` en doc-query-agent lee los archivos de `./docs`, los divide en fragmentos (chunks), les genera embeddings con `nomic-embed-text` (un modelo local de Ollama), y los guarda en Qdrant.

Un **embedding** es un array de números que representa el "significado" de un texto. Textos semánticamente similares tienen embeddings parecidos.

### Paso 2: Query

Cuando llega una pregunta:
1. La pregunta también se convierte en embedding
2. Qdrant busca los chunks cuyo embedding es más cercano al de la pregunta (similaridad coseno)
3. Los chunks relevantes se pasan como contexto al LLM junto con la pregunta
4. El LLM genera la respuesta

En código, Spring AI hace todo esto con `QuestionAnswerAdvisor`:

```java
ChatClientResponse response = chatClient.prompt()
    .user(request.question())
    .call()
    .chatClientResponse();
// response.context() ya tiene los documentos recuperados de Qdrant
```

---

## 7. Cómo funciona el smart-search-agent (tool calling)

Tool calling (o function calling) le da al LLM la capacidad de "llamar herramientas". En lugar de responder directamente, el LLM puede decir "necesito la fecha de hoy" y el framework llama a una función Java que devuelve ese dato.

El flujo es:
1. La pregunta + la lista de tools disponibles van al LLM
2. El LLM decide si necesita alguna tool o puede responder solo
3. Si necesita una tool, Spring AI ejecuta el método Java anotado con `@Tool`
4. El resultado de la tool se le pasa de vuelta al LLM
5. El LLM genera la respuesta final con esa información

Este agente usa **Gemini Flash** (modelo cloud) porque llama a búsquedas web y necesita un modelo que soporte function calling de forma confiable. `llama3.2:3b` no lo soporta bien con esta configuración.

---

## 8. Qué hace cada capa de la stack

| Componente | Lenguaje | Rol |
|------------|----------|-----|
| `orchestrator-agent` | Java / Spring Boot | Router deterministico, punto de entrada |
| `doc-query-agent` | Java / Spring Boot + Spring AI | RAG sobre documentos locales |
| `diagnosis-agent` | Java / Spring Boot + Spring AI | Diagnóstico estructurado de logs |
| `smart-search-agent` | Java / Spring Boot + Spring AI | Tool calling con Gemini |
| `LiteLLM` | Python (proxy) | Gateway unificado de modelos |
| `Ollama` | Go (runtime) | Corre modelos locales |
| `Qdrant` | Rust (vector DB) | Guarda y busca embeddings |
| `Flowise` | Node.js (low-code) | Armado visual de flujos |
| `Langfuse` | Next.js + Postgres | Observabilidad y trazabilidad |

---

## 9. Cómo levantar el stack por primera vez (nueva PC)

### Prerequisitos

- **Docker Desktop** instalado y corriendo
- **Git** para clonar el repo
- Para desarrollo local de los agentes Java: **Java 21** y **Maven 3.9+**

### Pasos

**1. Clonar el repo**
```bash
git clone <url-del-repo>
cd genai-stack
```

**2. Crear el archivo `.env`** en la raíz del proyecto (Docker lo lee automáticamente):
```
GEMINI_API_KEY=tu-clave-de-google-ai-studio
OPENAI_API_KEY=sk-proj-...
```
> El `.env` está en `.gitignore` — nunca se sube al repo. Cada desarrollador lo crea localmente.

**3. Levantar todos los servicios**
```bash
docker compose up --build
```
La primera vez tarda varios minutos porque:
- Descarga las imágenes de Docker (Qdrant, LiteLLM, Ollama, Flowise, etc.)
- Compila los cuatro agentes Java desde cero
- Ollama descarga los modelos locales

**4. Descargar los modelos en Ollama** (solo la primera vez, mientras los contenedores corren):
```bash
docker exec ollama ollama pull llama3.2:3b
docker exec ollama ollama pull nomic-embed-text
```

**5. Ingestar la documentación** en doc-query-agent (solo la primera vez o cuando cambian los docs):
```bash
curl -X POST http://localhost:8080/api/v1/ingest
```

**6. Verificar que todo esté arriba**
```bash
docker compose ps
```
Todos los servicios deben estar en estado `Up`.

### Comandos del día a día

```bash
# Levantar todo
docker compose up -d

# Ver logs de un servicio específico
docker compose logs -f orchestrator-agent

# Parar todo
docker compose down

# Parar y borrar volúmenes (Qdrant, Ollama models — ojo: borra los embeddings)
docker compose down -v
```

---

## 10. Cómo redesplegar después de un cambio

### Si solo cambiaste configuración (litellm_config.yaml, docker-compose.yaml)

Recreá solo el contenedor afectado:
```bash
docker compose up -d --force-recreate litellm
```

### Si cambiaste código Java de un agente

Rebuild y recreá solo ese agente:
```bash
docker compose up -d --build orchestrator-agent
```

### Si cambiaste docker-compose.yaml y querés que todos los contenedores tomen los cambios

```bash
docker compose up -d --force-recreate
```

### Si hacés `git pull` con cambios en el código Java

```bash
git pull
docker compose up -d --build
```
Solo se reconstruyen los agentes cuyos archivos cambiaron (Docker usa caché de capas).

---

## 11. Cómo agregar un nuevo agente

1. Creá la carpeta `agents/nuevo-agente/` con la estructura hexagonal estándar
2. Agregá el servicio en `docker-compose.yaml`
3. Declarar la URL del nuevo agente en `AppProperties.java` del orchestrator si querés que el orchestrator lo use
4. Agregar la lógica de ruteo en `RuleBasedRoutingClassifier` y el método de invocación en `DownstreamAgentClient`

---

## 12. Glosario rápido

| Término | Qué es |
|---------|--------|
| **LLM** | Large Language Model — el modelo de IA que genera texto (GPT-4, Gemini, llama3) |
| **Embedding** | Representación numérica de un texto que captura su significado semántico |
| **RAG** | Retrieval-Augmented Generation — buscar contexto relevante antes de generar texto |
| **Tool calling** | Capacidad de un LLM de invocar funciones Java/Python para obtener datos |
| **Vector store** | Base de datos especializada en buscar embeddings por similitud (acá usamos Qdrant) |
| **Prompt** | El texto que le mandás al LLM como instrucción o pregunta |
| **Token** | Unidad de texto que procesa el LLM (~¾ de una palabra en inglés) |
| **Arquitectura hexagonal** | Patrón de diseño que separa dominio, aplicación e infraestructura en capas |
| **Port** | Interfaz Java que define qué puede hacer un componente (sin implementar el cómo) |
| **Adapter** | Implementación concreta de un port (el código real que hace el trabajo) |
