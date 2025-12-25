package io.jmix.ai.mcpdocs.util.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Base wrapper for MCP JSON-RPC responses.
 */
public class McpResponse {
    protected final JsonNode root;

    public McpResponse(JsonNode root) {
        this.root = root;
    }

    public boolean hasResult() {
        return root.has("result");
    }

    public boolean hasError() {
        return root.has("error");
    }

    public String getErrorMessage() {
        if (!hasError()) {
            return null;
        }
        JsonNode error = root.get("error");
        return error.has("message") ? error.get("message").asText() : error.toString();
    }

    protected JsonNode getResult() {
        return root.get("result");
    }

    /**
     * Wrapper for tools/list response.
     */
    public static class ToolListResponse extends McpResponse {
        public ToolListResponse(JsonNode root) {
            super(root);
        }

        public List<Tool> getTools() {
            if (!hasResult()) {
                return List.of();
            }
            JsonNode tools = getResult().get("tools");
            if (tools == null || !tools.isArray()) {
                return List.of();
            }

            List<Tool> result = new ArrayList<>();
            for (JsonNode toolNode : tools) {
                result.add(new Tool(toolNode));
            }
            return result;
        }

        public Tool findTool(String name) {
            return getTools().stream()
                    .filter(tool -> tool.getName().equals(name))
                    .findFirst()
                    .orElse(null);
        }

        public boolean hasTool(String name) {
            return findTool(name) != null;
        }
    }

    /**
     * Wrapper for tool definition.
     */
    public static class Tool {
        private final JsonNode node;

        public Tool(JsonNode node) {
            this.node = node;
        }

        public String getName() {
            return node.has("name") ? node.get("name").asText() : null;
        }

        public String getDescription() {
            return node.has("description") ? node.get("description").asText() : null;
        }

        public boolean hasDescription() {
            return node.has("description");
        }
    }

    /**
     * Wrapper for tools/call response.
     */
    public static class ToolCallResponse extends McpResponse {
        public ToolCallResponse(JsonNode root) {
            super(root);
        }

        public List<Content> getContent() {
            if (!hasResult()) {
                return List.of();
            }
            JsonNode content = getResult().get("content");
            if (content == null || !content.isArray()) {
                return List.of();
            }

            List<Content> result = new ArrayList<>();
            for (JsonNode contentNode : content) {
                result.add(new Content(contentNode));
            }
            return result;
        }

        public String getTextContent() {
            return getContent().stream()
                    .filter(c -> "text".equals(c.getType()))
                    .map(Content::getText)
                    .findFirst()
                    .orElse(null);
        }

        public boolean containsText(String substring) {
            String text = getTextContent();
            return text != null && text.contains(substring);
        }

        /**
         * Checks if the result has isError field set to true.
         * This is the MCP convention for tool execution errors.
         */
        public boolean hasToolError() {
            if (!hasResult()) {
                return false;
            }
            JsonNode isError = getResult().get("isError");
            return isError != null && isError.asBoolean();
        }

        @Override
        public boolean hasError() {
            // Check both JSON-RPC error and MCP tool error
            return super.hasError() || hasToolError();
        }
    }

    /**
     * Wrapper for content item.
     */
    public static class Content {
        private final JsonNode node;

        public Content(JsonNode node) {
            this.node = node;
        }

        public String getType() {
            return node.has("type") ? node.get("type").asText() : null;
        }

        public String getText() {
            return node.has("text") ? node.get("text").asText() : null;
        }
    }
}
