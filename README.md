# Jmix Docs MCP Tool

Minimal MCP server exposing a single tool that searches Jmix content (documentation, training examples, UI samples) via an AI backend with vector search and reranking.
The LLM calls this tool with a free-form text query, and the backend finds the most relevant snippets from the indexed Jmix knowledge base.
Inside the backend, embeddings and a reranker first collect candidate documents, then filter and sort them so that only truly useful fragments remain in the final result.
The calling LLM can optionally pick the Jmix version, cap the number of returned snippets and set an approximate response token budget.
The MCP server simply passes the list of snippets back to the LLM as JSON, without adding extra logic or modifying the content.


## Flow Overview

```mermaid
sequenceDiagram
    participant U as User
    participant A as Agent / LLM
    participant MCP as MCP Server (JmixDocsTool)
    participant Telemetry as McpToolTelemetry + TimedExecutor
    participant SearchSvc as JmixContentSearchService
    participant Backend as Jmix AI Backend (/api/v2/search)
    participant VS as Vector Store + Reranker

    U->>A: "How to configure Jmix security?"
    A->>MCP: callTool("search-jmix-docs", queryText [, jmixVersion, maxResults, tokens])
    MCP->>Telemetry: executeWithTelemetry(context)
    Telemetry->>SearchSvc: search(queryText, options)
    SearchSvc->>Backend: POST /api/v2/search {query, jmix_version?, max_results?, tokens?}
    Backend->>VS: semantic search + rerank
    VS-->>Backend: relevance-ordered snippets (JSON)
    Backend-->>SearchSvc: JSON String
    SearchSvc-->>Telemetry: JSON String
    Telemetry-->>MCP: CallToolResult(text=JSON)
    MCP-->>A: tool result (snippets JSON)
    A-->>U: answer with citations/snippets

```

## Flow Overview(png)
![schema.png](resources/schema.png)


## MCP Tool

MCP contract (conceptual):

```json
{
  "name": "search-jmix-docs",
  "description": "Search Jmix documentation using semantic search with reranking. Returns relevance-ordered snippets with title, source URL and content.",
  "input": {
    "type": "object",
    "properties": {
      "queryText": {
        "type": "string",
        "description": "Search query for Jmix documentation"
      },
      "jmixVersion": {
        "type": "string",
        "description": "Jmix major version to search: 'v2' or 'v3'. Omit to search the current Jmix release."
      },
      "maxResults": {
        "type": "integer",
        "description": "Maximum total number of returned snippets, 1-50. Omit for the server default."
      },
      "tokens": {
        "type": "integer",
        "description": "Approximate response token budget, 1-100000; the most relevant snippets are kept. Omit to return the full result set."
      }
    },
    "required": ["queryText"]
  }
}
```

## Backend API (internal)

```http
POST /api/v2/search
Content-Type: application/json

{
  "query": "<text>",
  "jmix_version": "v2 | v3, optional",
  "max_results": "1-50, optional",
  "tokens": "1-100000, optional"
}
```

Optional fields are sent only when the corresponding tool argument is provided; the backend
defaults the version to the current Jmix release and returns its default result set otherwise.

Response: a JSON array of relevance-ordered snippets, each with `id`, `title`, `source` and
`content` (returned as raw String to the LLM).

## Transport

The server supports two MCP transports simultaneously:

| Transport | Endpoint | Status |
|-----------|----------|--------|
| **Streamable HTTP** | `POST /mcp` | Primary (recommended) |
| **SSE** | `GET /sse` + `POST /message` | Backward compatibility |

Streamable HTTP is the default per MCP spec 2025-03-26. SSE is kept for legacy clients and can be disabled via `mcp.server.sse-compat.enabled=false`.

## Development
1. Run [Jmix AI Backend](https://github.com/jmix-framework/jmix-ai-backend)
2. Run Application (`./gradlew bootRun`)
3. Add MCP to project.

**Streamable HTTP (recommended):**

- For Claude Code: `claude mcp add --transport http jmixdocs http://localhost:8080/mcp`
- Config:
```json
{
    "jmixdocs": {
      "type": "streamable-http",
      "url": "http://localhost:8080/mcp"
    }
}
```

**SSE (legacy):**

- For Claude Code: `claude mcp add --transport sse jmixdocs http://localhost:8080/sse`
- Config:
```json
{
    "jmixdocs": {
      "type": "sse",
      "url": "http://localhost:8080/sse"
    }
}
```

## Rate Limiting & Validation

Multi-layer protection via `McpRequestValidator`:
- **Per-IP rate limiting** — 300 req/hour (Bucket4j)
- **Global rate limiting** — 800 req/min (DDoS protection)
- **Token budgets** — per-IP and global limits across minute/hour/day windows
- **Input validation** — max query length 10k chars, max estimated tokens 2k

## Notes

* All retrieval, embedding and reranking logic lives in the AI backend.
* `McpToolTelemetry` + `TimedExecutor` add timing, structured logging and MCP logging notifications around tool calls.


