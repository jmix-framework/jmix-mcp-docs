package io.jmix.ai.mcpdocs.util.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.ai.mcpdocs.util.JsonRpcRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Simple MCP test client for integration testing.
 * Implements Streamable HTTP transport and JSON-RPC protocol.
 */
public class McpTestClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(McpTestClient.class);
    private static final String MCP_SESSION_ID_HEADER = "Mcp-Session-Id";

    private final String baseUrl;
    private final String mcpEndpoint;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private String sessionId;

    public McpTestClient(String baseUrl) {
        this(baseUrl, "/mcp");
    }

    public McpTestClient(String baseUrl, String mcpEndpoint) {
        this.baseUrl = baseUrl;
        this.mcpEndpoint = mcpEndpoint;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void connect() throws Exception {
        logger.info("Connecting to MCP server: {}{}", baseUrl, mcpEndpoint);
        initialize();
    }

    private void initialize() throws Exception {
        JsonNode initRequest = JsonRpcRequest.initialize("init").build();
        JsonNode response = postAndGetResponse(initRequest);
        logger.info("Initialized: {}", response.has("result"));

        JsonNode notification = JsonRpcRequest.initializedNotification().build();
        postNotification(notification);
    }

    public McpResponse.ToolCallResponse callTool(String toolName, Map<String, Object> arguments) throws Exception {
        String id = "call-" + System.currentTimeMillis();
        JsonNode request = JsonRpcRequest.callTool(id, toolName, arguments).build();
        JsonNode response = postAndGetResponse(request);
        return new McpResponse.ToolCallResponse(response);
    }

    public McpResponse.ToolListResponse listTools() throws Exception {
        JsonNode request = JsonRpcRequest.listTools("list-tools").build();
        JsonNode response = postAndGetResponse(request);
        return new McpResponse.ToolListResponse(response);
    }

    /**
     * Posts a JSON-RPC request and returns the parsed response.
     * Handles both direct JSON responses and SSE-streamed responses.
     */
    private JsonNode postAndGetResponse(JsonNode body) throws Exception {
        String url = baseUrl + mcpEndpoint;
        String json = objectMapper.writeValueAsString(body);

        logger.debug("POST {}: {}", url, json.substring(0, Math.min(200, json.length())));

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(30));

        if (sessionId != null) {
            requestBuilder.header(MCP_SESSION_ID_HEADER, sessionId);
        }

        HttpResponse<java.io.InputStream> response = httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofInputStream()
        );

        if (response.statusCode() != 200) {
            String responseBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("POST failed: " + response.statusCode() + " " + responseBody);
        }

        response.headers().firstValue(MCP_SESSION_ID_HEADER).ifPresent(id -> {
            this.sessionId = id;
            logger.debug("Session ID: {}", id);
        });

        String contentType = response.headers().firstValue("Content-Type").orElse("");

        if (contentType.contains("text/event-stream")) {
            return parseStreamResponse(response);
        } else {
            String responseBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            logger.debug("Response: {}", responseBody.substring(0, Math.min(200, responseBody.length())));
            return objectMapper.readTree(responseBody);
        }
    }

    /**
     * Parses an SSE-streamed response, returning the first JSON-RPC message found.
     */
    private JsonNode parseStreamResponse(HttpResponse<java.io.InputStream> response) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

            StringBuilder dataBuffer = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (!data.isEmpty()) {
                        dataBuffer.append(data);
                    }
                } else if (line.isEmpty() && !dataBuffer.isEmpty()) {
                    // End of SSE event — try to parse as JSON-RPC response
                    try {
                        JsonNode json = objectMapper.readTree(dataBuffer.toString());
                        if (json.has("id") || json.has("result") || json.has("error")) {
                            logger.debug("SSE response: {}", dataBuffer.toString()
                                    .substring(0, Math.min(200, dataBuffer.length())));
                            return json;
                        }
                    } catch (Exception e) {
                        logger.debug("Non-JSON SSE event: {}", dataBuffer.toString()
                                .substring(0, Math.min(50, dataBuffer.length())));
                    }
                    dataBuffer.setLength(0);
                }
            }

            if (!dataBuffer.isEmpty()) {
                return objectMapper.readTree(dataBuffer.toString());
            }

            throw new IOException("No JSON-RPC response found in SSE stream");
        }
    }

    private void postNotification(JsonNode body) throws Exception {
        String url = baseUrl + mcpEndpoint;
        String json = objectMapper.writeValueAsString(body);

        logger.debug("POST notification {}: {}", url, json.substring(0, Math.min(200, json.length())));

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(30));

        if (sessionId != null) {
            requestBuilder.header(MCP_SESSION_ID_HEADER, sessionId);
        }

        HttpResponse<String> response = httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        );

        // Notifications may return 200 or 202 (accepted)
        if (response.statusCode() != 200 && response.statusCode() != 202
                && response.statusCode() != 204) {
            throw new IOException("Notification failed: " + response.statusCode() + " " + response.body());
        }

        response.headers().firstValue(MCP_SESSION_ID_HEADER).ifPresent(id -> {
            this.sessionId = id;
        });
    }

    public String getClientIp() {
        return "127.0.0.1";
    }

    @Override
    public void close() {
        logger.debug("Closing MCP test client");
    }
}
