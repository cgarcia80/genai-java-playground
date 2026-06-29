# Setup and Initialization Guide (WSL / Linux)

This guide walks you through setting up and running the Generative AI multi-agent stack on a new machine using Windows Subsystem for Linux (WSL) or a native Linux environment.

## Prerequisites

Ensure the following tools are installed on your WSL instance / host system:

1. **Docker & Docker Compose**: 
   - If using Windows, install [Docker Desktop](https://www.docker.com/products/docker-desktop/) and enable **WSL 2 Integration** under `Settings > Resources > WSL Integration`.
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
   - Make sure your SSH keys or HTTPS credentials are configured.

## Step 1: Clone the Repository

Clone the project to your WSL home directory (do not clone it to `/mnt/c/...` for performance and file permission reasons):

```bash
git clone <your-repo-url>
cd genai-stack
```

## Step 2: Environment Configuration

The repository uses environment variables for API keys and secrets. 

1. Copy the template to create your local `.env` file (which is ignored by Git):
   ```bash
   cp .env.template .env
   ```
2. Open `.env` and fill in your keys (e.g., `GEMINI_API_KEY`, `LANGFUSE_` keys, etc.):
   ```bash
   nano .env
   ```

## Step 3: Build the Java Agents

Before running the containers, compile all Java agents to generate the JAR files required by the Docker builds:

```bash
mvn clean package -DskipTests
```

This compiles:
- `doc-query-agent`
- `diagnosis-agent`
- `smart-search-agent`
- `orchestrator-agent`

## Step 4: Launch the Services

Start all services in detached mode using Docker Compose:

```bash
docker compose --env-file .env up -d --build
```

Verify that all containers are up and healthy:
```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

## Step 5: Model Pulling (Ollama)

Since vector databases and Ollama data volumes are local to each Docker host, you need to download the models in the new environment.

Execute the following commands to pull the required LLM and Embedding models:

```bash
# Pull LLM
docker exec -it ollama ollama pull llama3.2:3b

# Pull Embedding model (used for doc-query RAG)
docker exec -it ollama ollama pull nomic-embed-text
```

## Step 6: Ingest Initial Documents

For the `doc-query-agent` (and consequently `smart-search` and `orchestrator`) to search documentation, you must ingest the files in the `./docs` folder into Qdrant:

```bash
curl -X POST http://localhost:8080/api/v1/ingest
```

Expected response:
```json
{"filesProcessed": 1, "chunksLoaded": 42}
```

## Verification

Refer to [USAGE.md](file:///C:/Users/cesar/Documents/ai-tools-export/genai-stack/USAGE.md) for detailed smoke test commands to verify each agent.
