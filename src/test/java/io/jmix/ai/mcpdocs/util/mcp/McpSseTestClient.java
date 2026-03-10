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
import java.util.*;
import java.util.concurrent.*;

/**
 * SSE-based MCP test client for verifying backward compatibility.
 * Uses the deprecated SSE transport (GET /sse + POST /message).
 */
public class McpSseTestClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(McpSseTestClient.class);

    private final String baseUrl;
    private final String sseEndpoint;
    private final String messageEndpoint;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private String sessionId;
    private final BlockingQueue<SseEvent> eventQueue;
    private Thread sseThread;
    private volatile boolean running;

    public McpSseTestClient(String baseUrl) {
        this(baseUrl, "/sse", "/message");
    }

    public McpSseTestClient(String baseUrl, String sseEndpoint, String messageEndpoint) {
        this.baseUrl = baseUrl;
        this.sseEndpoint = sseEndpoint;
        this.messageEndpoint = messageEndpoint;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.eventQueue = new LinkedBlockingQueue<>();
    }

    public void connect() throws Exception {
        logger.info("Connecting to MCP server via SSE: {}", baseUrl);
        running = true;

        sseThread = new Thread(this::listenSse, "SSE-Listener");
        sseThread.start();

        Thread.sleep(1000);

        try {
            sessionId = waitForSessionId();
            logger.info("Got sessionId from SSE: {}", sessionId);
        } catch (TimeoutException e) {
            sessionId = UUID.randomUUID().toString();
            logger.info("Generated sessionId: {}", sessionId);
        }

        initialize();
    }

    private void listenSse() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + sseEndpoint))
                    .header("Accept", "text/event-stream")
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() != 200) {
                throw new IOException("SSE connection failed with status: " + response.statusCode());
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

                List<String> buffer = new ArrayList<>();
                String line;

                while (running && (line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (!buffer.isEmpty()) {
                            SseEvent event = parseEvent(buffer);
                            if (event != null) {
                                eventQueue.offer(event);
                                logger.debug("SSE event: {}", event.data);
                            }
                            buffer.clear();
                        }
                    } else {
                        buffer.add(line);
                    }
                }
            }
        } catch (InterruptedException e) {
            logger.debug("SSE listener interrupted during shutdown");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (running) {
                logger.error("SSE listener failed", e);
            } else {
                logger.debug("SSE listener stopped during shutdown");
            }
        }
    }

    private SseEvent parseEvent(List<String> lines) {
        StringBuilder data = new StringBuilder();
        String event = null;
        String id = null;

        for (String line : lines) {
            if (line.startsWith("data:")) {
                if (!data.isEmpty()) data.append("\n");
                data.append(line.substring(5).trim());
            } else if (line.startsWith("event:")) {
                event = line.substring(6).trim();
            } else if (line.startsWith("id:")) {
                id = line.substring(3).trim();
            }
        }

        if (!data.isEmpty()) {
            return new SseEvent(data.toString(), event, id);
        }
        return null;
    }

    private String waitForSessionId() throws Exception {
        long deadline = System.currentTimeMillis() + 3000;

        while (System.currentTimeMillis() < deadline) {
            SseEvent event = eventQueue.poll(500, TimeUnit.MILLISECONDS);
            if (event == null) continue;

            if (event.data.contains("sessionId=")) {
                return event.data.split("sessionId=", 2)[1].trim();
            }

            try {
                JsonNode json = objectMapper.readTree(event.data);
                if (json.has("sessionId")) {
                    return json.get("sessionId").asText();
                }
                if (json.has("result") && json.get("result").has("sessionId")) {
                    return json.get("result").get("sessionId").asText();
                }
            } catch (Exception ignored) {
            }
        }

        throw new TimeoutException("No sessionId received");
    }

    private void initialize() throws Exception {
        JsonNode initRequest = JsonRpcRequest.initialize("init").build();
        post(initRequest);
        JsonNode response = awaitResponse("init", 10);
        logger.info("Initialized via SSE: {}", response.has("result"));

        JsonNode notification = JsonRpcRequest.initializedNotification().build();
        post(notification);
    }

    public McpResponse.ToolCallResponse callTool(String toolName, Map<String, Object> arguments) throws Exception {
        String id = "call-" + System.currentTimeMillis();
        JsonNode request = JsonRpcRequest.callTool(id, toolName, arguments).build();
        post(request);
        JsonNode response = awaitResponse(id, 30);
        return new McpResponse.ToolCallResponse(response);
    }

    public McpResponse.ToolListResponse listTools() throws Exception {
        JsonNode request = JsonRpcRequest.listTools("list-tools").build();
        post(request);
        JsonNode response = awaitResponse("list-tools", 10);
        return new McpResponse.ToolListResponse(response);
    }

    private void post(JsonNode body) throws Exception {
        String url = baseUrl + messageEndpoint + "?sessionId=" + sessionId;
        String json = objectMapper.writeValueAsString(body);

        logger.debug("POST {}: {}", url, json.substring(0, Math.min(200, json.length())));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("POST failed: " + response.statusCode() + " " + response.body());
        }
    }

    private JsonNode awaitResponse(String id, int timeoutSeconds) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;

        while (System.currentTimeMillis() < deadline) {
            SseEvent event = eventQueue.poll(1, TimeUnit.SECONDS);
            if (event == null) continue;

            try {
                JsonNode json = objectMapper.readTree(event.data);
                if (json.has("id") && json.get("id").asText().equals(id)) {
                    if (json.has("error")) {
                        throw new RuntimeException("RPC error: " + json.get("error"));
                    }
                    logger.debug("Got response for id={}", id);
                    return json;
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                logger.debug("Non-JSON event: {}", event.data.substring(0, Math.min(50, event.data.length())));
            }
        }

        throw new TimeoutException("No response for id=" + id);
    }

    public String getClientIp() {
        return "127.0.0.1";
    }

    @Override
    public void close() {
        logger.debug("Closing SSE MCP test client");
        running = false;

        if (sseThread != null) {
            try {
                sseThread.join(100);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            if (sseThread.isAlive()) {
                sseThread.interrupt();
                try {
                    sseThread.join(500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public record SseEvent(String data, String event, String id) {
    }
}
