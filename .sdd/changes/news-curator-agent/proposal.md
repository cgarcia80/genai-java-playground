# Technical Proposal: News Curator Agent & Dynamic RAG

## 1. Intent
Create a News Curator Agent and Dynamic RAG (Retrieval-Augmented Generation) system in Java using Spring Boot. The agent will fetch relevant news stories from HackerNews based on a topic keyword, index them with local embeddings in a vector database (Qdrant), and expose a query endpoint to perform RAG using OpenAI's `gpt-4o-mini`, tracking traces with Langfuse.

## 2. Scope

### In Scope
*   **New Spring Boot Module**: Located at `agents/news-curator-agent`, configured to run on port `8084`.
*   **Curate Endpoint (`POST /api/v1/curate`)**:
    *   Accepts: `{"topic": "string", "limit": int}`.
    *   Behavior:
        *   Searches HackerNews stories using the public and free HackerNews v0 API via Firebase (`https://hacker-news.firebaseio.com/v0/`).
        *   Retrieves details (title, URL, author, text/score) for stories.
        *   Generates local embeddings for the fetched stories.
        *   Ingests the items into the Qdrant vector database under the collection `news-curator`.
*   **Query Endpoint (`POST /api/v1/query`)**:
    *   Accepts: `{"question": "string"}`.
    *   Behavior:
        *   Retrieves relevant context from the `news-curator` Qdrant collection using vector search.
        *   Sends the user query along with the retrieved context to `gpt-4o-mini` (via LLM Integration).
        *   Returns a consolidated answer, explicitly citing the source titles and URLs of the original HackerNews posts.
*   **Observability**: Integrated observability using Langfuse to trace LLM calls, vector retrieval, and pipeline latency.

### Out of Scope
*   **UI/Frontend**: All interactions will be headless (via API endpoints).
*   **Relational Database Persistence**: No PostgreSQL/MySQL storage is required. Metadata and text will reside directly in Qdrant payloads.
*   **External Web Scraping**: The system will not scrape or read external links/web pages cited by HackerNews posts. Only the story titles, text (if present), author, and metadata directly returned by the HackerNews API will be embedded and indexed.

## 3. Architecture & Technical Decisions
*   **Language & Framework**: Java 21, Spring Boot 3.x.
*   **Vector Search**: Qdrant.
*   **Embeddings**: Local embedding generation (e.g., using ONNX models or Spring AI local embedding providers) to avoid external APIs for vector generation.
*   **LLM Provider**: OpenAI `gpt-4o-mini` for the RAG prompt synthesis.
*   **Observability**: Langfuse SDK integration for Java/Spring Boot.
*   **HackerNews Integration**: Since HN Firebase API does not support native keyword search directly, we will use Algolia's HN Search API (e.g., `https://hn.algolia.com/api/v1/search?query=topic`) to fetch matching story IDs, then fetch the full details of those stories using the Firebase HN API to ensure high-fidelity schema retrieval, or perform direct extraction.

## 4. Success Criteria
1.  **Maven Build**: The project compiles successfully using standard Maven commands.
2.  **Vector Ingestion**: Initiating a POST request to `/api/v1/curate` successfully populates Qdrant with HackerNews stories and local embeddings.
3.  **RAG Verification**: A query to `/api/v1/query` returns a coherent answer synthesized from the ingested data, accompanied by the corresponding HackerNews post titles and URLs as references.

## 5. Risks & Mitigation
*   **HackerNews API Rate Limiting / Latency**: Querying multiple stories sequentially might trigger rate limits or take too long.
    *   *Mitigation*: Parallel fetch calls (e.g., via Virtual Threads or WebClient) and leveraging Algolia's search index to target only the top relevant posts.
*   **Local Embeddings Latency on CPU**: Running embedding models locally on CPU can introduce noticeable response times.
    *   *Mitigation*: Use a lightweight embedding model (e.g., `all-MiniLM-L6-v2`) and optimize thread pooling.
