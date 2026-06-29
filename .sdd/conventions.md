# Project Conventions

> Leído por todos los sub-agentes SDD antes de actuar. Tiene precedencia sobre los defaults genéricos.
> Las secciones marcadas con ⚠️ son sugerencias — verificar contra el código real cuando exista.
> Las secciones sin ⚠️ fueron inferidas del contexto del proyecto durante `sdd-init`.

## Arquitectura general

- Los agentes SIEMPRE le pegan a LiteLLM (`http://litellm:4000` dentro de Docker, `http://localhost:4000` desde el host). NUNCA directo a Ollama.
- El modelo concreto es intercambiable por config en `litellm_config.yaml`. El código Java no hardcodea nombres de modelo.
- Cada agente sigue arquitectura hexagonal: el modelo de IA es un adapter de salida detrás de un puerto. La lógica del agente no sabe qué modelo hay detrás.
- Cada agente tiene dominio ACOTADO. Un agente, una responsabilidad.

## Privacidad y ruteo de modelos

- Agentes que tocan documentación interna o datos sensibles → modelo LOCAL (Ollama). Configurado en `litellm_config.yaml`. Nada sale a internet.
- Agentes de resolución genérica sin datos internos → PUEDEN usar modelo cloud, declarado explícitamente en su sección de `litellm_config.yaml`.
- La regla "datos sensibles → local" DEBE quedar visible en config, no escondida en lógica. Ante la duda: local.

## Docker Compose

- Un servicio por concern. Sin mezclar responsabilidades en un mismo contenedor.
- Volúmenes nombrados para datos persistentes. Los directorios de datos (`*_data`, `*_storage`) están en `.gitignore`.
- `restart: unless-stopped` para todos los servicios de infraestructura.
- Variables de entorno sensibles (secrets, passwords) van en `.env` (no commiteado), referenciadas con `${VAR}` en el compose.
- Redes: usar red interna Docker para comunicación entre servicios (nombre de servicio como hostname). Exponer al host solo los puertos necesarios.
- Orden de arranque: usar `depends_on` para dependencias explícitas.

## LiteLLM

- Config en `litellm_config.yaml` en la raíz del repo.
- Cada modelo declarado con: `model_name` (alias OpenAI-compatible), `litellm_params.model` (ej: `ollama/llama3.2:3b`), y `litellm_params.api_base`.
- El alias de modelo que usan los agentes Java es el `model_name` de LiteLLM, no el nombre nativo de Ollama.
- Routing de privacidad: documentado en comentarios dentro de `litellm_config.yaml`.

## Agentes Java (Spring Boot)

Estas convenciones aplican cuando se creen los proyectos Java de agentes:

- **Stack**: Java 21, Spring Boot 3.x, Spring AI.
- **Arquitectura**: hexagonal (ports & adapters).
  - `domain`: entidades, value objects, ports (interfaces). Sin anotaciones de framework.
  - `application`: use cases, orquestación. Depende solo de `domain`.
  - `infrastructure`: adapters (Spring AI, Qdrant client, REST), configs.
- **Inyección**: constructor injection siempre. Nada de `@Autowired` en fields.
- **Config**: ⚠️ `@ConfigurationProperties` con records (preferido) o `@Value` — definir por agente.
- **Transacciones**: `@Transactional` solo en capa de aplicación, nunca en controllers.
- **LLM client**: Spring AI `ChatClient` configurado para apuntar a LiteLLM (OpenAI-compatible). El `model` se inyecta por config, no hardcodeado.
- **Embeddings**: Spring AI `EmbeddingModel` (para RAG) también via LiteLLM o Ollama directo según el caso.

## Testing

- Infraestructura Docker: smoke tests manuales con `curl` después de cada cambio. El `curl` de verificación se documenta en el `quick-note.md` o en la sección de validación del change.
- Agentes Java (cuando existan): JUnit 5 + Mockito para unit tests. `@SpringBootTest` + Testcontainers para integration tests.
- Nombre de tests: `should<Expected>When<Condition>()`.
- ⚠️ Cobertura mínima: definir cuando se construya el primer agente.

## Commits y branching ⚠️

- El orquestador SDD no hace commits. El desarrollador los maneja.
- ⚠️ Definir convención: Conventional Commits u otro formato.

## Observabilidad

- Langfuse captura traces de LLM automáticamente si los agentes configuran las variables `LANGFUSE_*`.
- Logs estructurados (JSON en prod, texto en local).
- Nunca loguear tokens, API keys, ni contenido de documentación interna en logs.

## Seguridad

- API keys de modelos cloud: en `.env`, nunca hardcodeadas ni commiteadas.
- Datos de documentación interna: nunca salen del entorno local. Documentado en config de LiteLLM.
- ⚠️ Autenticación de los endpoints REST de agentes: definir cuando se construya el primer agente.

## Secciones pendientes de verificación

Las secciones marcadas con ⚠️ son sugerencias sin evidencia del código real (que aún no existe).
Actualizar cuando se construya el primer agente Java:

- [ ] `## Agentes Java` → confirmar estilo de config (`@Value` vs `@ConfigurationProperties`)
- [ ] `## Testing` → definir cobertura mínima del equipo
- [ ] `## Commits y branching` → definir convención de mensajes
- [ ] `## Seguridad` → definir autenticación de endpoints REST de agentes
