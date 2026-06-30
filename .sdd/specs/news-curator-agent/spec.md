# Specification: News Curator Agent

This specification defines the functional requirements and API contracts for the News Curator Agent.

## 1. REST API Contract

The News Curator Agent runs on port `8084` and exposes the following endpoints.

### 1.1 Curate Stories
*   **Path**: `POST /api/v1/curate`
*   **Content-Type**: `application/json`
*   **Request Body**:
    ```json
    {
      "topic": "string",
      "limit": int
    }
    ```
    *   `topic`: The keyword or subject to search for on HackerNews (required, must not be empty).
    *   `limit`: The maximum number of stories to fetch and index (required, must be positive).
*   **Response Body (Success - 200 OK)**:
    ```json
    {
      "status": "success",
      "itemsCurated": int
    }
    ```
*   **Response Body (Bad Request - 400 Bad Request)**:
    ```json
    {
      "status": "error",
      "message": "string"
    }
    ```

### 1.2 Query (RAG)
*   **Path**: `POST /api/v1/query`
*   **Content-Type**: `application/json`
*   **Request Body**:
    ```json
    {
      "question": "string"
    }
    ```
    *   `question`: The user's query or prompt to be answered using the indexed HackerNews stories (required, must not be empty).
*   **Response Body (Success - 200 OK)**:
    ```json
    {
      "answer": "string",
      "sources": [
        {
          "title": "string",
          "url": "string"
        }
      ]
    }
    ```
    *   `answer`: The LLM-synthesized answer based on the retrieved context.
    *   `sources`: List of source HackerNews posts (titles and URLs) retrieved as context. Can be empty.

---

## 2. Requirements & Scenarios

### Requirement 1: News Curation Endpoint
The system must fetch top news stories matching a keyword from HackerNews, generate local embeddings, and index them into Qdrant.

#### Scenario: Curación exitosa con tópicos válidos (Successful curation with valid topics)
*   **Given** the HackerNews API is functional.
*   **And** the Qdrant database is running with the `news-curator` collection.
*   **When** a client sends a `POST /api/v1/curate` request with a valid topic (e.g., `"Java 21"`) and a limit of `5`.
*   **Then** the agent fetches stories matching the topic from HackerNews.
*   **And** generates local embeddings for each story.
*   **And** upserts the vectors and metadata (title, URL, author, score) into Qdrant.
*   **And** returns a `200 OK` response with `{"status": "success", "itemsCurated": 5}` (or the actual number of curated stories if fewer than limit are found).

#### Scenario: Curación con campos inválidos o ausentes (Curation with invalid or missing fields)
*   **Given** the curate endpoint is active.
*   **When** a client sends a `POST /api/v1/curate` request where:
    *   `topic` is null or empty, OR
    *   `limit` is null, zero, or negative.
*   **Then** the agent rejects the request.
*   **And** returns a `400 Bad Request` status code.
*   **And** a response body describing the validation error.

#### Scenario: Error en la integración de HackerNews API (HackerNews API integration error)
*   **Given** the HackerNews API (or Algolia search API) is down, slow, or returning error responses.
*   **When** a client sends a `POST /api/v1/curate` request with a valid topic and limit.
*   **Then** the agent fails to fetch the stories.
*   **And** returns a `502 Bad Gateway` status code.
*   **And** a response body describing the upstream integration failure.

### Requirement 2: Retrieval-Augmented Generation (RAG) Query Endpoint
The system must perform vector search on Qdrant using local embeddings, feed the context to OpenAI's `gpt-4o-mini`, and return a synthesized response citing original sources.

#### Scenario: Consulta RAG válida con fuentes asociadas (Valid RAG query with associated sources)
*   **Given** the Qdrant database has indexed stories about `"GraalVM"`.
*   **And** the OpenAI API is functional.
*   **When** a client sends a `POST /api/v1/query` request with `"What is GraalVM and its benefits?"`.
*   **Then** the agent generates local embedding for the query.
*   **And** queries Qdrant for the most similar stories.
*   **And** constructs a prompt including the user question and retrieved stories as context.
*   **And** calls `gpt-4o-mini` to synthesize the answer.
*   **And** returns a `200 OK` response where:
    *   `answer` contains the LLM response.
    *   `sources` is a non-empty array of objects containing the `title` and `url` of the retrieved stories.

#### Scenario: Consulta RAG sin contexto en Qdrant (RAG query with no context in Qdrant)
*   **Given** the Qdrant database has no stories matching the topic `"Quantum Computing"`.
*   **And** the OpenAI API is functional.
*   **When** a client sends a `POST /api/v1/query` request with `"What is the latest news on Quantum Computing?"`.
*   **Then** the agent queries Qdrant and retrieves zero relevant documents.
*   **And** calls `gpt-4o-mini` with instructions to politely inform the user that no local context was found.
*   **And** returns a `200 OK` response where:
    *   `answer` is a friendly response indicating a lack of local context.
    *   `sources` is an empty list `[]`.
