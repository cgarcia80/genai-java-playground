# Guía Técnica: Aprendé cómo funciona este stack de IA

> **Para quién es esto:** alguien que sabe programar pero es nuevo en el mundo de la IA generativa, LLMs y Spring AI. No asume conocimientos previos sobre ninguno de esos temas.

---

## Ruta de aprendizaje recomendada

Seguí el orden. Cada sección depende de la anterior.

| Paso | Sección | Tiempo estimado |
|------|---------|----------------|
| 1 | Glosario mínimo — leelo antes que nada | 5 min |
| 2 | El problema que resuelve este stack | 5 min |
| 3 | El mapa completo explicado | 10 min |
| 4 | LiteLLM: el proxy que une todo | 10 min |
| 5 | Spring AI: el cliente de LLMs | 10 min |
| 6 | RAG: cómo el LLM "lee" tus documentos | 15 min |
| 7 | Tool calling: cómo el LLM "actúa" | 10 min |
| 8 | Arquitectura hexagonal: por qué el código está dividido así | 15 min |
| 9 | Tour por el código del orchestrator | 20 min |
| 10 | Setup en una PC nueva | 30 min (práctica) |
| 11 | Ejercicios progresivos | a tu ritmo |

---

## Sección 1 — Glosario mínimo

Leé esto primero. Estos términos van a aparecer en todo el documento y necesitás entenderlos antes de continuar.

**LLM** (Large Language Model)
El modelo de IA que genera texto. GPT-4, Gemini, llama3 son LLMs. No es "el sistema que piensa" — es un modelo estadístico entrenado en texto que predice qué palabras vienen después de otras. Muy bueno para lenguaje natural, muy malo para hechos recientes o datos privados.

**Prompt**
El texto que le mandás al LLM como instrucción o pregunta. "Explicame qué es RAG" es un prompt. La calidad del prompt determina la calidad de la respuesta.

**Token**
Unidad de texto que procesa el LLM. Aproximadamente ¾ de una palabra en inglés. Importa porque los modelos tienen un límite de tokens y los modelos cloud cobran por token.

**Embedding**
Una representación numérica de un texto (un array de 768 o 1536 números) que captura su significado semántico. Textos con significado similar tienen embeddings parecidos numéricamente.
*Analogía:* pensá en las coordenadas GPS. Dos ciudades cercanas tienen coordenadas parecidas. Los embeddings hacen lo mismo pero con significado: "perro" y "can" tienen coordenadas semánticas muy cercanas. "perro" y "avión" están lejos. El sistema puede medir esa distancia matemáticamente.

**Vector store**
Base de datos especializada en guardar y buscar embeddings por similitud. Acá usamos Qdrant.
*Analogía:* una biblioteca común te permite buscar libros por título o autor. Un vector store te permite buscar por contenido semántico: "dame los libros que traten temas parecidos a esta frase", sin importar si comparten palabras exactas.

**RAG** (Retrieval-Augmented Generation)
Técnica para que un LLM pueda responder sobre información que no tenía en su entrenamiento (como tus documentos internos). Primero buscás los fragmentos relevantes en el vector store, después se los pasás al LLM junto con la pregunta.
*Analogía:* es la diferencia entre un examen de libro cerrado (el LLM responde de memoria, puede equivocarse) y uno de libro abierto (primero buscás la info relevante, después respondés con esa info en mano).

**Tool calling** (o function calling)
Capacidad de algunos LLMs de "pedir" que se ejecute una función de tu código para obtener información. El LLM dice "necesito la fecha de hoy" y tu código ejecuta `LocalDate.now()` y le devuelve el resultado.
*Analogía:* un consultor muy inteligente encerrado en una sala sin ventanas. Puede razonar perfectamente, pero si necesita un dato del mundo real, le pasa una nota a su asistente por debajo de la puerta. El asistente va a buscarlo y le devuelve la respuesta. El LLM es el consultor; las tools son el asistente.

**Arquitectura hexagonal**
Patrón de diseño que separa el código en capas: dominio (lógica pura), aplicación (casos de uso) e infraestructura (frameworks, HTTP, bases de datos). Permite cambiar un componente sin tocar el resto.
*Analogía:* el puerto USB de tu notebook. La notebook define el contrato ("acepto dispositivos USB"). No le importa si conectás un pendrive, un mouse o un hub — cada uno es un adaptador distinto de la misma interfaz. En el código, `OrchestrationPort` es el puerto USB. `OrchestrationAdapter` es el dispositivo de hoy. Mañana podés conectar otro sin tocar la notebook.

---

## Sección 2 — El problema que resuelve este stack

Imaginá que tenés una empresa con:
- Documentación técnica interna (PDFs, Markdown)
- Logs de errores de producción
- Equipos que hacen preguntas en lenguaje natural sobre todo eso

Los LLMs "de fábrica" no sirven para esto porque **no conocen tu información**. GPT-4 no sabe nada de tus procesos internos, tu arquitectura, o los errores de tu sistema. Si le preguntás, inventa.

Este stack resuelve tres problemas concretos:

| Problema | Solución en este stack |
|----------|----------------------|
| El LLM no conoce mi documentación | RAG: ingestamos los docs, buscamos fragmentos relevantes y se los pasamos como contexto |
| El LLM no puede hacer búsquedas o calcular cosas | Tool calling: le damos herramientas que puede invocar |
| No sé a cuál de los tres servicios mandar cada pregunta | Orchestrator: clasifica la pregunta y deriva al agente correcto |

---

## Sección 3 — El mapa completo explicado

```
Tu request HTTP
      │
      ▼
orchestrator-agent (:8083)   ← analizás la pregunta, decidís quién responde
      │
      ├─► doc-query-agent (:8080)    ← "buscá en la documentación"
      ├─► diagnosis-agent (:8081)    ← "diagnosticá este error"
      └─► smart-search-agent (:8082) ← "respondé con tools"
              │
              ▼
         LiteLLM (:4000)   ← proxy: todos los agentes hablan con él
              │
              ├─► Ollama   → llama3.2:3b, nomic-embed-text  (local, gratis)
              ├─► Google   → gemini-flash                   (cloud, pago)
              └─► OpenAI   → gpt-4o-mini, gpt-4o            (cloud, pago)
```

**Lo importante:** los agentes Java nunca hablan directamente con OpenAI o Google. Siempre hablan con LiteLLM. Esto es intencional — lo vas a entender en la siguiente sección.

**Infraestructura de soporte:**
- **Qdrant**: guarda los embeddings para RAG. doc-query-agent lo usa.
- **Flowise**: interfaz visual para armar flujos sin código (para experimentar).
- **Langfuse**: registra cada llamada al LLM para que puedas ver tokens, latencia y costos.

---

## Sección 4 — LiteLLM: el proxy que une todo

### El problema sin LiteLLM

Cada proveedor de LLM tiene su propia API:
- OpenAI: `POST https://api.openai.com/v1/chat/completions`
- Google Gemini: `POST https://generativelanguage.googleapis.com/v1beta/models/...`
- Ollama: `POST http://localhost:11434/api/chat`

Si escribís código Java que habla con OpenAI y después querés probar con Gemini, tenés que reescribir el cliente HTTP, el manejo de errores, el parseo de la respuesta, y los tests.

### La solución: un proxy en el medio

**Analogía:** LiteLLM es como un adaptador de enchufes universal. Tu dispositivo (el agente Java) tiene un solo tipo de ficha. El adaptador te deja conectarlo a cualquier toma de corriente del mundo (OpenAI, Gemini, Ollama) sin modificar el dispositivo.

LiteLLM expone **una sola API** compatible con el formato de OpenAI. Todos los agentes Java le hablan a `http://litellm:4000` usando siempre el mismo formato. LiteLLM se encarga de redirigir al proveedor real.

### Cómo se declaran los modelos

En `litellm_config.yaml`:

```yaml
- model_name: gpt-4o-mini          # nombre que pide Java ("el alias")
  litellm_params:
    model: openai/gpt-4o-mini       # modelo real al que LiteLLM llama
    api_key: "os.environ/OPENAI_API_KEY"  # lee la key del entorno del contenedor
```

Cuando un agente Java dice "usá el modelo `gpt-4o-mini`", LiteLLM intercepta, ve que ese alias apunta a `openai/gpt-4o-mini`, toma la key del entorno, y llama a los servidores de OpenAI.

### El contrato de nombres — esto es crítico

El valor que ponés en `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL` (en el compose o en `application.properties`) **debe coincidir exactamente** con el `model_name` declarado en `litellm_config.yaml`.

```
litellm_config.yaml          docker-compose.yaml
─────────────────────        ──────────────────────────────────
model_name: gpt-4o-mini  ←→  SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL=gpt-4o-mini
model_name: gemini-flash ←→  SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL=gemini-flash
model_name: llama3       ←→  SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL=llama3
```

Si ponés un nombre que no existe en `litellm_config.yaml`, el agente va a fallar con un error de modelo no encontrado. LiteLLM no adivina — busca el alias exacto.

### Probalo

Con el stack corriendo, podés llamar a LiteLLM directamente como si fuera OpenAI:

```bash
curl http://localhost:4000/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d "{
    \"model\": \"gpt-4o-mini\",
    \"messages\": [{\"role\": \"user\", \"content\": \"Hola, ¿qué modelo sos?\"}]
  }"
```

Cambiá `gpt-4o-mini` por `gemini-flash` o `llama3` — el formato del request es idéntico.

---

## Sección 5 — Spring AI: el cliente de LLMs para Java

### El problema sin Spring AI

Llamar a un LLM desde Java requiere:
- Construir el request JSON a mano
- Manejar el HTTP con `HttpClient` o similar
- Parsear la respuesta
- Manejar streaming (si querés respuestas progresivas)
- Repetir todo esto para cada proveedor

### La solución: una abstracción

**Analogía:** Spring AI es para LLMs lo mismo que Spring Data JPA es para bases de datos. En lugar de escribir SQL a mano y parsear `ResultSet`, usás una interfaz de alto nivel. Spring AI te da `ChatClient` para hablar con cualquier LLM.

```java
// Sin Spring AI tendrías 30 líneas de HTTP boilerplate.
// Con Spring AI:
String respuesta = chatClient.prompt()
    .user("¿Qué es la arquitectura hexagonal?")
    .call()
    .content();
```

### Cómo apunta a LiteLLM

En `src/main/resources/application.properties` de cada agente:

```properties
spring.ai.openai.base-url=http://litellm:4000   # apunta al proxy, no a OpenAI
spring.ai.openai.api-key=no-needed              # LiteLLM maneja las keys
spring.ai.openai.chat.options.model=gpt-4o-mini # alias declarado en litellm_config.yaml
```

Spring AI cree que está hablando con OpenAI. En realidad habla con LiteLLM. LiteLLM redirige a donde corresponda.

### Para cambiar el modelo de un agente

Solo cambiás la variable de entorno en `docker-compose.yaml` — sin recompilar:

```yaml
smart-search-agent:
  environment:
    - SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL=gpt-4o   # antes: gpt-4o-mini
```

```bash
docker compose up -d smart-search-agent   # reiniciás solo ese contenedor
```

> **Nota:** esto aplica a `doc-query-agent`, `diagnosis-agent` y `smart-search-agent`. El `orchestrator-agent` no usa Spring AI — su clasificación es local y no necesita LLM.

---

## Sección 6 — RAG: cómo el LLM "lee" tus documentos

### El problema

Los LLMs tienen una fecha de corte de entrenamiento y no conocen tu información privada. Si le preguntás a GPT-4 sobre la arquitectura de tu sistema, inventa una respuesta plausible pero incorrecta. Esto se llama "alucinación".

### La solución en dos fases

**Analogía:** RAG es como la diferencia entre un examen de libro cerrado y uno de libro abierto. Sin RAG, el LLM responde de memoria (y puede equivocarse). Con RAG, el LLM puede "abrir el libro" antes de responder.

#### Fase 1: Ingestión (se hace una vez)

```
Documentos (PDF, Markdown)
      │
      ▼
Dividir en fragmentos (chunks) de ~500 tokens
      │
      ▼
Generar embedding de cada chunk
(nomic-embed-text en Ollama → array de 768 números)
      │
      ▼
Guardar en Qdrant (junto con el texto original)
```

```bash
# Disparás esto una vez (o cuando cambian los docs):
curl -X POST http://localhost:8080/api/v1/ingest
```

#### Fase 2: Query (cada vez que llega una pregunta)

```
"¿Qué es la arquitectura hexagonal?"
      │
      ▼
Generar embedding de la pregunta
      │
      ▼
Buscar en Qdrant los chunks con embedding más cercano
(similaridad coseno — encuentra los fragmentos "sobre el mismo tema")
      │
      ▼
Construir el prompt:
"Contexto: [chunk 1] [chunk 2] [chunk 3]
 Pregunta: ¿Qué es la arquitectura hexagonal?
 Respondé basándote solo en el contexto."
      │
      ▼
LLM genera la respuesta
```

El LLM no "leyó" tus documentos — cada vez recibe los fragmentos relevantes como parte del prompt.

### Dónde está en el código

**Archivo:** `agents/doc-query-agent/src/main/java/.../adapter/ai/SpringAiDocumentQueryAdapter.java`

Spring AI hace todo el trabajo de búsqueda y construcción del prompt con `QuestionAnswerAdvisor`:

```java
ChatClientResponse response = chatClient.prompt()
    .user(request.question())
    .call()
    .chatClientResponse();
// response.context() ya tiene los chunks recuperados de Qdrant
```

### Probalo

```bash
# Primero ingestás (si no lo hiciste):
curl -X POST http://localhost:8080/api/v1/ingest

# Después preguntás:
curl -X POST http://localhost:8080/api/v1/query \
  -H "Content-Type: application/json" \
  -d "{\"question\": \"¿Qué tecnologías usa este proyecto?\"}"
```

La respuesta va a citar información de los archivos en `./docs`. Si preguntás algo que no está en los docs, va a decir que no encontró información relevante — no inventa.

---

## Sección 7 — Tool calling: cómo el LLM "actúa"

### El problema

Un LLM sabe mucho, pero no puede hacer cosas: no puede consultar una API, no sabe qué hora es, no puede buscar en internet. Todo lo que sabe está fijo en su entrenamiento.

### La solución

**Analogía:** pensá en el LLM como un consultor muy inteligente que trabaja desde una habitación sin ventanas. Puede pensar y razonar muy bien, pero si necesita datos del mundo exterior, le pasa una nota a su asistente, el asistente sale a buscar la info, y vuelve con la respuesta.

Con tool calling, el flujo es:

```
Pregunta: "¿Qué fecha es hoy y cuántos archivos procesó el sistema?"
      │
      ▼
LLM evalúa: "Necesito dos tools: getCurrentDate y getProcessedCount"
      │
      ▼
Spring AI ejecuta los métodos Java anotados con @Tool
      │
      ▼
Resultados vuelven al LLM: "Hoy es 29/06/2026, procesados: 142"
      │
      ▼
LLM genera respuesta final usando esos datos
```

### Dónde está en el código

En `smart-search-agent`, los métodos Java anotados con `@Tool` son las "herramientas" que el LLM puede invocar. Spring AI le informa al LLM qué tools existen y qué hacen, y cuando el LLM "pide" una, Spring AI la ejecuta automáticamente.

### Probalo

```bash
curl -X POST http://localhost:8082/api/v1/chat \
  -H "Content-Type: application/json" \
  -d "{\"question\": \"¿Qué fecha es hoy?\"}"
```

*Respuesta esperada:* `{"answer":"Hoy es 29 de junio de 2026.","toolsUsed":["getCurrentDate"]}`

> **Por qué este agente usa Gemini y no llama3?** El tool calling requiere un modelo que entienda bien cuándo invocar herramientas y con qué parámetros. `llama3.2:3b` (el modelo local) no es confiable para esto con nuestra configuración. Gemini Flash sí.

---

## Sección 8 — Arquitectura hexagonal: por qué el código está dividido así

### El problema sin esta arquitectura

Imaginá que tu código de lógica de negocio llama directamente a `RestTemplate` para hacer HTTP, usa anotaciones de JPA para la base de datos, y referencia clases de Spring en todos lados. Si querés:
- Testear la lógica sin levantar un servidor HTTP → muy difícil
- Cambiar de Spring a Quarkus → reescribís todo
- Reemplazar el LLM por otro → el cambio se esparce por todo el código

### La solución: capas con una regla de oro

**Las capas internas no conocen a las externas.** El dominio no sabe que existe Spring. Los casos de uso no saben que la respuesta se manda por HTTP.

```
┌──────────────────────────────────────────┐
│  infrastructure/  (Spring, HTTP, LiteLLM) │
│  ┌────────────────────────────────────┐  │
│  │  application/  (casos de uso)      │  │
│  │  ┌──────────────────────────────┐  │  │
│  │  │  domain/  (Java puro)        │  │  │
│  │  │  sin Spring, sin HTTP        │  │  │
│  │  └──────────────────────────────┘  │  │
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

**`domain/`** — modelos e interfaces puras. No importa ningún framework.
- `OrchestrationRequest` — record Java con `question`
- `OrchestrationResult` — record Java con `answer` y `routedTo`
- `RoutingTarget` — enum: `DOC_QUERY`, `DIAGNOSIS`, `SMART_SEARCH`
- `OrchestrationPort` — *interfaz* que define qué puede hacer el sistema

**`application/`** — casos de uso. Orquesta el dominio, no toca infraestructura.
- `OrchestrateUseCase` — recibe request, llama al puerto

**`infrastructure/`** — adaptadores concretos. Aquí vive Spring Boot.
- `OrchestratorController` — recibe HTTP, llama al use case
- `RuleBasedRoutingClassifier` — implementa la clasificación
- `DownstreamAgentClient` — hace HTTP a los otros agentes
- `OrchestrationAdapter` — implementa `OrchestrationPort`

**Analogía:** el puerto USB de tu notebook. Tu notebook tiene un contrato ("acepto dispositivos USB"). No le importa si conectás un pendrive, un mouse o un hub. Cada dispositivo es un adaptador distinto de la misma interfaz. `OrchestrationPort` es el puerto USB. `OrchestrationAdapter` es el dispositivo conectado hoy. Mañana podés conectar otro sin tocar la notebook.

**El beneficio práctico:** si querés pasar de clasificación por reglas a clasificación por LLM, solo reemplazás `RuleBasedRoutingClassifier`. `OrchestrateUseCase`, `OrchestrationPort` y el controller no se modifican.

---

## Sección 9 — Tour por el código del orchestrator-agent

Ahora que entendés los conceptos, recorremos el código más representativo de este stack: el orchestrator. Es el más instructivo porque no usa LLM propio — se puede leer de punta a punta sin conocer Spring AI.

### El viaje de una request

Vamos de afuera hacia adentro:

```
POST /api/v1/ask {"question": "Tengo un NullPointerException..."}
         │
         ▼
[1] OrchestratorController          → recibe el HTTP request
         │
         ▼
[2] OrchestrateUseCase              → delega al puerto
         │
         ▼
[3] OrchestrationAdapter            → coordina clasificación e invocación
         │
         ▼
[4] RuleBasedRoutingClassifier      → decide: DIAGNOSIS
         │
         ▼
[5] DownstreamAgentClient           → POST a diagnosis-agent:8081
         │
         ▼
[6] OrchestrationResult             → answer + "diagnosis-agent"
         │
         ▼
HTTP 200 {"answer": "...", "routedTo": "diagnosis-agent"}
```

### [1] El Controller — entrada HTTP

**Archivo:** `infrastructure/adapter/rest/OrchestratorController.java`

Recibe el request, lee el header opcional `X-Bypass-Routing`, y llama al use case. Si viene el header, saltea la clasificación. Si no, usa la automática.

### [2] El Use Case — lógica de aplicación

**Archivo:** `application/usecase/OrchestrateUseCase.java`

```java
public OrchestrationResult orchestrate(OrchestrationRequest request) {
    return port.orchestrate(request);
}

public OrchestrationResult bypass(OrchestrationRequest request, RoutingTarget target) {
    return port.invoke(request, target);
}
```

Deliberadamente simple. Solo delega. La complejidad está en los adaptadores.

### [3] El Adapter — conecta clasificación e invocación

**Archivo:** `infrastructure/adapter/orchestration/OrchestrationAdapter.java`

```java
public OrchestrationResult orchestrate(OrchestrationRequest request) {
    return invoke(request, routingClassifier.classify(request));
}

public OrchestrationResult invoke(OrchestrationRequest request, RoutingTarget target) {
    String answer = switch (target) {
        case DOC_QUERY   -> downstreamAgentClient.callDocQueryAgent(request.question());
        case DIAGNOSIS   -> downstreamAgentClient.callDiagnosisAgent(request.question());
        case SMART_SEARCH -> downstreamAgentClient.callSmartSearchAgent(request.question());
    };
    return new OrchestrationResult(answer, target.agentName());
}
```

El `switch` exhaustivo sobre el enum garantiza que si agregás un target nuevo, el compilador te avisa que falta el caso.

### [4] El Clasificador — reglas locales

**Archivo:** `infrastructure/adapter/routing/RuleBasedRoutingClassifier.java`

```java
public RoutingTarget classify(OrchestrationRequest request) {
    String question = normalized(request.question());  // → minúsculas
    if (isDiagnosis(question)) return RoutingTarget.DIAGNOSIS;
    if (isDocQuery(question))  return RoutingTarget.DOC_QUERY;
    return RoutingTarget.SMART_SEARCH;                 // default
}
```

Prioridad: **diagnosis > doc-query > smart-search**. Un stacktrace puede mencionar "documentación" pero si tiene `Exception` va a diagnosis.

`isDiagnosis()` detecta:
- Regex de stacktrace Java: `at com.example.Service.java:42`
- Keywords: `exception`, `error`, `failed`, `caused by`, `traceback`, `fallo`

`isDocQuery()` detecta:
- Keywords: `documentación`, `arquitectura`, `entidad`, `dominio`, `proceso`

### [5] El Cliente HTTP — habla con los otros agentes

**Archivo:** `infrastructure/adapter/http/DownstreamAgentClient.java`

Tres métodos, uno por agente. Cada uno sabe qué endpoint llamar y cómo transformar la respuesta:

```java
// doc-query: {question} → {answer}
public String callDocQueryAgent(String question) { ... }

// diagnosis: {log} → {rootCause, location, suggestion} → String aplanado
public String callDiagnosisAgent(String logContent) { ... }

// smart-search: {question} → {answer}
public String callSmartSearchAgent(String question) { ... }
```

Si el downstream no responde → lanza `DownstreamAgentException` → `GlobalExceptionHandler` la convierte en HTTP 502.

---

## Sección 10 — Setup en una PC nueva (después de git pull)

### Prerequisitos

Instalá esto una sola vez:
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) — para correr todos los contenedores
- Git — para clonar el repo
- *Opcional (solo si vas a tocar código Java):* Java 21 + Maven 3.9+

### Pasos

**1. Clonar el repo**
```bash
git clone <url-del-repo>
cd genai-stack
```

**2. Crear el archivo `.env`** en la raíz (Docker lo lee automáticamente):
```
GEMINI_API_KEY=tu-clave-de-google-ai-studio
OPENAI_API_KEY=sk-proj-...
```
> Este archivo está en `.gitignore`. Nunca se sube al repo. Si no tenés alguna key, podés dejar la variable vacía y ese modelo simplemente no va a funcionar.

**3. Levantar todo**
```bash
docker compose up --build
```
La primera vez tarda varios minutos. Descarga imágenes, compila los agentes Java y levanta todos los servicios.

**4. Descargar modelos locales** (mientras los contenedores corren, en otra terminal):
```bash
docker exec ollama ollama pull llama3.2:3b
docker exec ollama ollama pull nomic-embed-text
```

**5. Ingestar la documentación** (solo la primera vez o cuando cambian los docs):
```bash
curl -X POST http://localhost:8080/api/v1/ingest
```

**6. Verificar**
```bash
docker compose ps   # todos deben estar en estado "Up"
```

### Comandos del día a día

```bash
docker compose up -d                          # levantar todo en background
docker compose down                           # parar todo
docker compose logs -f orchestrator-agent     # ver logs de un servicio
docker compose ps                             # ver estado de cada contenedor
```

### Cómo redesplegar después de un cambio

```bash
# Cambié litellm_config.yaml o docker-compose.yaml
docker compose up -d --force-recreate litellm

# Cambié código Java de un agente
docker compose up -d --build orchestrator-agent

# Hice git pull con cambios en varios agentes
docker compose up -d --build
```

---

## Sección 11 — Ejercicios progresivos

Hacelos en orden. Cada uno construye sobre el anterior.

### Nivel 1 — Explorar (sin tocar código)

**Ejercicio 1.1:** Hacé una pregunta al orchestrator con un error de Java y confirmá que `routedTo` es `diagnosis-agent`:
```bash
curl -X POST http://localhost:8083/api/v1/ask \
  -H "Content-Type: application/json" \
  -d "{\"question\": \"java.lang.NullPointerException at com.example.Service:42\"}"
```

**Ejercicio 1.2:** Hacé una pregunta sobre documentación y confirmá que `routedTo` es `doc-query-agent`:
```bash
curl -X POST http://localhost:8083/api/v1/ask \
  -H "Content-Type: application/json" \
  -d "{\"question\": \"¿Qué entidades tiene el dominio?\"}"
```

**Ejercicio 1.3:** Usá el bypass para ir directo a smart-search sin importar la pregunta:
```bash
curl -X POST http://localhost:8083/api/v1/ask \
  -H "Content-Type: application/json" \
  -H "X-Bypass-Routing: smart-search" \
  -d "{\"question\": \"¿Qué arquitectura usamos?\"}"
```

**Ejercicio 1.4:** Comprobá que una pregunta de documentación bypasseada a diagnosis devuelve igual una respuesta (no un error):
```bash
curl -X POST http://localhost:8083/api/v1/ask \
  -H "Content-Type: application/json" \
  -H "X-Bypass-Routing: diagnosis" \
  -d "{\"question\": \"¿Qué es el dominio?\"}"
```

**Ejercicio 1.5:** Tirá un bypass con valor inválido y observá el HTTP 400:
```bash
curl -v -X POST http://localhost:8083/api/v1/ask \
  -H "Content-Type: application/json" \
  -H "X-Bypass-Routing: inventado" \
  -d "{\"question\": \"test\"}"
```

---

### Nivel 2 — Experimentar con modelos

**Ejercicio 2.1:** Cambiá el modelo del `smart-search-agent` a `gpt-4o-mini` (en `docker-compose.yaml`) y comparás la respuesta con la de Gemini para la misma pregunta.

**Ejercicio 2.2:** En `litellm_config.yaml`, declarás un alias nuevo llamado `my-model` que apunta a `gpt-4o`. Reiniciás LiteLLM y lo llamás directo:
```bash
docker compose up -d --force-recreate litellm
curl http://localhost:4000/v1/models   # ¿aparece my-model?
```

---

### Nivel 3 — Modificar el clasificador

**Ejercicio 3.1:** Abrí `RuleBasedRoutingClassifier.java` y agregá la keyword `"crash"` a `ERROR_TERMS`. Reconstruí el contenedor y verificá que la pregunta `"tuve un crash en producción"` ahora rutea a `diagnosis-agent`.

**Ejercicio 3.2:** Agregá un cuarto `RoutingTarget` llamado `SECURITY` en el enum. Agregá keywords como `"vulnerabilidad"`, `"sql injection"`, `"xss"` al clasificador. ¿Qué más necesitás modificar para que el sistema no rompa? (Pista: el `switch` en `OrchestrationAdapter` es exhaustivo.)

---

### Nivel 4 — Agregar un agente nuevo

**Ejercicio 4.1:** Pensá (sin código): si quisieras agregar un `metrics-agent` que responde preguntas sobre métricas de performance, ¿qué archivos tendrías que modificar? Listalo antes de hacerlo.

---

## Recursos para seguir aprendiendo

- **Spring AI docs:** https://docs.spring.io/spring-ai/reference/
- **LiteLLM docs:** https://docs.litellm.ai/
- **Qdrant docs:** https://qdrant.tech/documentation/
- **Explicación de RAG con videos:** buscar "RAG explained" en YouTube — hay buenos en 10 minutos
- **Arquitectura hexagonal:** "Hexagonal Architecture" de Alistair Cockburn (el creador del patrón)
