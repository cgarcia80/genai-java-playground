# Guía de Configuración e Inicialización (WSL / Linux)

Esta guía te guiará en los pasos para configurar y ejecutar el stack multi-agente de IA Generativa en una nueva máquina usando Windows Subsystem for Linux (WSL) o un entorno Linux nativo.

## Requisitos Previos

Asegurate de tener instaladas las siguientes herramientas en tu instancia de WSL o sistema host:

1. **Docker & Docker Compose**: 
   - Si usás Windows, instalá [Docker Desktop](https://www.docker.com/products/docker-desktop/) y habilitá **WSL 2 Integration** en `Settings > Resources > WSL Integration`.
2. **Java Development Kit (JDK) 21**:
   ```bash
   sudo apt update
   sudo apt install openjdk-21-jdk
   ```
3. **Maven**:
   ```bash
   sudo apt install maven
   ```
4. **Git**:
   - Asegurate de tener configuradas tus claves SSH o credenciales HTTPS para clonar el repositorio.

## Paso 1: Clonar el Repositorio

Cloná el proyecto en el directorio home de tu WSL (no lo clones dentro de `/mnt/c/...` por cuestiones de rendimiento y permisos de archivos de Linux):

```bash
git clone <url-de-tu-repo>
cd genai-stack
```

## Paso 2: Configuración del Entorno

El repositorio utiliza variables de entorno para las API keys y secretos.

1. Copiá la plantilla para crear tu archivo local `.env` (el cual está ignorado en Git):
   ```bash
   cp .env.template .env
   ```
2. Abrí el archivo `.env` e ingresá tus claves reales (por ejemplo, `GEMINI_API_KEY`, las claves de `LANGFUSE_`, etc.):
   ```bash
   nano .env
   ```

## Paso 3: Compilar los Agentes Java

Antes de levantar los contenedores, tenés que compilar todos los agentes Java para generar los archivos JAR requeridos por los builds de Docker:

```bash
mvn clean package -DskipTests
```

Esto compilará:
- `doc-query-agent`
- `diagnosis-agent`
- `smart-search-agent`
- `orchestrator-agent`

## Paso 4: Levantar los Servicios

Iniciá todos los servicios en segundo plano usando Docker Compose:

```bash
docker compose --env-file .env up -d --build
```

Verificá que todos los contenedores estén corriendo y saludables:
```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

## Paso 5: Descargar Modelos (Ollama)

Dado que las bases de datos vectoriales y los volúmenes de datos de Ollama son locales de cada host de Docker, vas a necesitar descargar los modelos en el nuevo entorno.

Ejecutá los siguientes comandos para descargar el LLM y el modelo de embeddings necesarios:

```bash
# Descargar el LLM
docker exec -it ollama ollama pull llama3.2:3b

# Descargar el modelo de embeddings (usado para el RAG de doc-query)
docker exec -it ollama ollama pull nomic-embed-text
```

## Paso 6: Ingestar Documentos Iniciales

Para que el agente de consulta de documentos (`doc-query-agent`) y los demás agentes downstream puedan realizar búsquedas en la documentación, tenés que ingestar los archivos de la carpeta `./docs` en Qdrant:

```bash
curl -X POST http://localhost:8080/api/v1/ingest
```

Respuesta esperada:
```json
{"filesProcessed": 1, "chunksLoaded": 42}
```

## Verificación

Consultá el archivo [USAGE.md](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/USAGE.md) para ver los comandos de prueba de humo detallados y verificar el funcionamiento de cada agente.
