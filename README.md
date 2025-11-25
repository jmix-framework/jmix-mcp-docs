# Jmix Docs MCP Tool

Minimal MCP server exposing a single tool that searches Jmix content (documentation, training examples, UI samples) via an AI backend with vector search and reranking.
The LLM calls this tool with a free-form text query (in English or Russian), and the backend finds the most relevant pages from the indexed Jmix knowledge base.
Inside the backend, embeddings and a reranker first collect candidate documents, then filter and sort them so that only truly useful fragments remain in the final result.
The MCP server simply passes this list of documents back to the LLM as JSON, without adding extra logic or modifying the content.


## Flow Overview

```mermaid
sequenceDiagram
    participant U as User
    participant A as Agent / LLM
    participant MCP as MCP Server (JmixDocsTool)
    participant Telemetry as McpToolTelemetry + TimedExecutor
    participant SearchSvc as JmixContentSearchService
    participant Backend as Jmix AI Backend (/api/search)
    participant VS as Vector Store + Reranker

    U->>A: "How to configure Jmix security?"
    A->>MCP: callTool("search-jmix-docs", queryText)
    MCP->>Telemetry: executeWithTelemetry(context)
    Telemetry->>SearchSvc: searchForJmixDocs(queryText)
    SearchSvc->>Backend: POST /api/search {query, type:"docs"}
    Backend->>VS: semantic search + rerank
    VS-->>Backend: top-N doc chunks (JSON)
    Backend-->>SearchSvc: JSON String
    SearchSvc-->>Telemetry: JSON String
    Telemetry-->>MCP: CallToolResult(text=JSON)
    MCP-->>A: tool result (docs JSON)
    A-->>U: answer with citations/snippets

```

## Flow Overview(png)
![schema.png](resources/schema.png)


## MCP Tool

MCP contract (conceptual):

```json
{
  "name": "search-jmix-docs",
  "description": "Search Jmix documentation using semantic search with reranking",
  "input": {
    "type": "object",
    "properties": {
      "queryText": {
        "type": "string",
        "description": "Search query for Jmix documentation"
      }
    },
    "required": ["queryText"]
  }
}
```

## Backend API (internal)

```http
POST /api/search
Content-Type: application/json

{
  "query": "<text>",
  "type": "docs"
}
```

Response: JSON with top reranked Jmix doc chunks (returned as raw String to the LLM).

## Development
1. Run ![Jmix AI Backend](https://github.com/jmix-framework/jmix-ai-backend)
2. Run Application (`./gradlew bootRun`)
3. Add MCP to project

## Notes

* All retrieval, embedding and reranking logic lives in the AI backend.
* `McpToolTelemetry` + `TimedExecutor` add timing, structured logging and MCP logging notifications around tool calls.


