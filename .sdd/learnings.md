# Learnings — genai-stack

> Documentación de decisiones técnicas, patrones encontrados, y gotchas capturados durante el desarrollo del stack de agentes.
> Última actualización: 2026-06-30.

## 2026-06-30 — news-curator-agent

### Curación y RAG dinámico de noticias
- **What**: Curador de noticias basado en HackerNews API y RAG con almacenamiento vectorial en colecciones dedicadas de Qdrant.
- **Why**: Proveer respuestas actualizadas e informadas por eventos recientes sobre tecnologías de interés para el stack de GenAI.
- **Where**:
  - `agents/news-curator-agent/src/` (puerto `8084`)
  - Delta spec: `.sdd/specs/news-curator-agent/spec.md`
- **Learned**:
  - **Uso de CompletableFuture para llamadas concurrentes**: El diseño de la API de HackerNews requiere consultar cada ítem de forma individual tras obtener el listado de IDs destacados. El uso de `CompletableFuture` para paralelizar estas llamadas es crítico para mitigar la latencia de red y mejorar la experiencia de usuario durante la fase de curación.
  - **Potencia del RAG dinámico sobre colecciones dedicadas**: El aislamiento de tópicos y noticias dentro de colecciones vectoriales dedicadas y actualizadas en caliente en Qdrant optimiza los resultados de búsqueda semántica y evita la mezcla de contextos irrelevantes durante la generación.

## 2026-06-29 — orchestrator-agent (agente 4)

### Patrón multi-agente con ruteo local determinístico

- **What**: Construido `orchestrator-agent` como dispatcher central que delega a `doc-query-agent`, `diagnosis-agent` y `smart-search-agent` vía HTTP calls síncronos con `RestClient`. El ruteo es determinístico y basado en análisis local de palabras clave del prompt (sin invocar LLM).
- **Why**: El modelo `llama3.2:3b` no soporta tool calling en formato OpenAI vía LiteLLM — se cuelga sin responder. Cambio de diseño de tool-based LLM routing a rule-based local classification. Reduce quota consumida (sin LLM roundtrips para decidir) y elimina latencias de red innecesarias.
- **Where**: 
  - `agents/orchestrator-agent/src/` (nuevo módulo Spring Boot en `:8083`)
  - `docker-compose.yaml` (service `orchestrator-agent` agregado)
  - Delta spec: `.sdd/specs/orchestrator-agent/spec.md`
  - Tests: `OrchestratorControllerTest`, `RuleBasedRoutingClassifierTest`, `DownstreamAgentClientTest`
- **Learned**:
  - **Tres `RestClient` beans dedicados**: Un cliente por downstream (doc-query, diagnosis, smart-search) encapsula la URL base y timeouts. Trivial de mockear en tests y permite evolucionar timeout por destino sin afectar otros.
  - **Timeout de 90s para read, 5s para connect**: Los modelos locales pequeños en hardware limitado tardan (cold start + inference en 3B params). La lectura debe ser tolerante; la conexión debe ser rápida (esperamos que el agente esté listo).
  - **Aplastamiento de respuesta downstream a String**: Cada downstream devuelve estructura distinta (`doc-query` → `{answer, sources}`, `diagnosis` → `{rootCause, location, suggestion}`). Aplanar a `String` reduce tokens enviados al modelo (quota Gemini de 20 RPD es crítica) y simplifica el contrato de respuesta.
  - **ThreadLocal para tracking de tool invocado**: Patrón replicado de `smart-search-agent`: las `@Tool` métodos del orchestrator (si hubiese tool use) podrían registrar qué agente fue invocado. Aquí se usa para registrar el `routedTo` en el `X-Bypass-Routing` flow.
  - **Header `X-Bypass-Routing` para smoke dev**: Permite forzar un downstream específico sin pasar por el clasificador de ruteo. Crítico para testing local cuando queres validar que doc-query funciona sin que el router te redirija a diagnosis.
  - **`DownstreamAgentException` → HTTP 502**: Mapping explícito de fallos downstream a 502 Bad Gateway. El cliente sabe que el fallo fue en upstream, no un error de validación 4xx.

## 2026-06-29 — agentes 1, 2 y 3


### doc-query-agent (agente 1) — fix directo post-verify

- **similarity-threshold=0.70 era demasiado alto** para `nomic-embed-text` con texto en español. Los cosine similarity scores caen entre 0.45–0.65. Threshold bajo a 0.50 → RAG funciona.
- **Qdrant latest vs client 1.13.0**: siempre fijar la imagen de Qdrant a la versión compatible con el client de Spring AI. `qdrant:v1.13.1` con Spring AI 1.0.0.
- **Docker en WSL**: operar siempre desde WSL/Ubuntu con Docker Engine nativo. `docker-desktop` en estado `Uninstalling` contamina el contexto y produce falsos positivos.
- **El JAR del Dockerfile copia target/ pre-compilado**: cualquier cambio en `application.properties` o código Java requiere `mvn package` antes del `docker compose up --build`. El build de Docker no compila — solo copia el JAR.

### diagnosis-agent (agente 2) — patrón prompt directo

- **`@NotBlank` en records**: funciona con Jakarta Validation en Spring Boot 3.x + `@Valid` en `@RequestBody`.
- **ObjectMapper ya viene de Spring Boot**: no hace falta declarar un bean propio. Se inyecta directamente.
- **Extracción de JSON de respuesta LLM**: buscar primer `{` y último `}` en el contenido y parsear con Jackson. Fallback: poner el texto en `rootCause` si falla el parse. Necesario porque modelos locales pequeños no siempre respetan el formato pedido.

### smart-search-agent (agente 3) — patrón tool use

- **`llama3.2:3b` no soporta tool calling en formato OpenAI vía LiteLLM**: se cuelga sin responder ni loguear. Solo usar modelos que soporten función calling nativo (gemini, gpt-4, claude).
- **`@RequestScope` + Spring AI tools = problema**: Spring AI invoca los métodos `@Tool` en el mismo hilo HTTP, pero si el bean es `@RequestScope` y Spring AI llama desde un contexto diferente, falla. Solución: singleton + `ThreadLocal<List<String>>` para tracking de tools invocadas.
- **Spring AI auto-retry quema quota**: `spring.ai.retry.max-attempts` por defecto es 3. Con free tier de 20 RPD, 7 requests reales = quota agotada. Fijar `max-attempts=1` en agentes cloud.
- **Gemini free tier por modelo**: `gemini-2.5-flash` = 20 RPD. `gemini-1.5-flash` = 1500 RPD (pero requiere key tipo `AIzaSy`, no `AQ.`). La key `AQ.` solo da acceso a modelos nuevos con quota muy baja.
- **Tool use = 2 roundtrips**: primer call para que el modelo decida qué tool usar, segundo call con el resultado de la tool para que genere la respuesta final. Cada request al `/api/v1/chat` consume 2 créditos de quota.

## 2026-06-27 — fase-0-litellm

- **What**: Agregado LiteLLM como proxy OpenAI-compatible que centraliza el acceso al modelo Ollama. Docker service en compose con volumen de config, exponiendo `:4000`.
- **Why**: Los agentes Java (Fase 1+) nunca deben pegarle directo a Ollama. El proxy permite (a) intercambiar modelos sin tocar código, (b) rutear por privacidad vía `litellm_config.yaml`, (c) agregar observabilidad (callbacks a Langfuse) sin contaminar la lógica de agentes.
- **Where**: `docker-compose.yaml` (nuevo service `litellm`), `litellm_config.yaml` (nuevo archivo root).
- **Learned**:
  - **Imagen floating (main-latest)**: Útil para testing pero riesgoso en prod. Si LiteLLM falla al arrancar en el futuro, la primera sospecha es un breaking change en la imagen. Solución: fijar versión específica (e.g., `v1.55.0`) si el build se estabiliza.
  - **Dependencia explícita**: `depends_on: [ollama]` en compose es suficiente para garantizar order, pero no health. Si Ollama está arrancando al mismo tiempo que LiteLLM, puede ocurrir que LiteLLM no encuentre el modelo la primera vez. Solución en futuro: healthcheck en compose o retry logic en el cliente.
  - **Modelo alias es crucial**: El `model_name` en `litellm_config.yaml` (ej: `llama3`) es lo que los agentes usan en Spring AI. El nombre nativo de Ollama (`llama3.2:3b`) NUNCA debe hardcodearse en código Java. Mantener la config centralizada.
  - **Privacidad por config, no por código**: La regla "datos sensibles → modelo local" vive en `litellm_config.yaml`, no en if-statements de los agentes. Esto permite cambiar modelos por environment (dev: local, prod: cloud) sin recompilar.
