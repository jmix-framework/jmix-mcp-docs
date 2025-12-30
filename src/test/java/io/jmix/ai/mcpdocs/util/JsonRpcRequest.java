package io.jmix.ai.mcpdocs.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Fluent builder for JSON-RPC 2.0 requests.
 */
public class JsonRpcRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ObjectNode root;
    private ObjectNode params;

    private JsonRpcRequest(String id, String method) {
        this.root = MAPPER.createObjectNode();
        root.put("jsonrpc", "2.0");
        if (id != null) {
            root.put("id", id);
        }
        root.put("method", method);
    }

    public static JsonRpcRequest request(String id, String method) {
        return new JsonRpcRequest(id, method);
    }

    public static JsonRpcRequest notification(String method) {
        return new JsonRpcRequest(null, method);
    }

    public static JsonRpcRequest initialize(String id) {
        return new JsonRpcRequest(id, "initialize")
                .param("protocolVersion", "2024-11-05")
                .objectParam("capabilities")
                .objectParam("clientInfo", Map.of(
                        "name", "mcp-test-client",
                        "version", "1.0"
                ));
    }

    public static JsonRpcRequest initializedNotification() {
        return notification("notifications/initialized")
                .emptyParams();
    }

    public static JsonRpcRequest listTools(String id) {
        return new JsonRpcRequest(id, "tools/list")
                .emptyParams();
    }

    public static JsonRpcRequest callTool(String id, String toolName, Map<String, Object> arguments) {
        return new JsonRpcRequest(id, "tools/call")
                .param("name", toolName)
                .param("arguments", arguments);
    }

    public JsonRpcRequest emptyParams() {
        ensureParams();
        return this;
    }

    public JsonRpcRequest param(String key, String value) {
        ensureParams();
        params.put(key, value);
        return this;
    }

    public JsonRpcRequest param(String key, Map<String, Object> value) {
        ensureParams();
        params.set(key, MAPPER.valueToTree(value));
        return this;
    }

    public JsonRpcRequest objectParam(String key) {
        ensureParams();
        params.putObject(key);
        return this;
    }

    public JsonRpcRequest objectParam(String key, Map<String, String> values) {
        ensureParams();
        ObjectNode obj = params.putObject(key);
        values.forEach(obj::put);
        return this;
    }

    private void ensureParams() {
        if (params == null) {
            params = root.putObject("params");
        }
    }

    public JsonNode build() {
        if (params == null) {
            root.putObject("params");
        }
        return root;
    }
}
