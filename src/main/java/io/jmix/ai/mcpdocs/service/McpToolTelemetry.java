package io.jmix.ai.mcpdocs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.ai.mcpdocs.util.TimedExecutor;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.abbreviate;

@Component
public class McpToolTelemetry {

    private final ObjectMapper objectMapper;

    public McpToolTelemetry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public McpSchema.CallToolResult executeWithTelemetry(ToolExecutionContext context, Supplier<String> operation) {
        Logger logger = LoggerFactory.getLogger(context.toolClass);
        TimedExecutor timedExecutor = new TimedExecutor(context.toolClass);

        logToolStart(logger, context);

        TimedExecutor.TimedResult<String> result = timedExecutor.execute(
                "Tool execution: " + context.toolName,
                operation
        );

        if (result.hasError()) {
            logToolError(logger, context, result.getDurationMs(), result.error().getMessage());

            return McpSchema.CallToolResult.builder()
                    .addTextContent("Error: " + result.error().getMessage())
                    .isError(true)
                    .build();
        }

        logToolSuccess(logger, context, result.getDurationMs());

        return McpSchema.CallToolResult.builder()
                .addTextContent(result.result())
                .build();
    }

    private void logToolStart(Logger logger, ToolExecutionContext context) {
        String formattedParams = formatParams(context.params);
        logger.info(">>> [START] Tool: {}, Params: {}", context.toolName, formattedParams);

        sendNotification(context, McpSchema.LoggingLevel.INFO,
                String.format("[START] Processing with params: %s", formattedParams));
    }

    private void logToolSuccess(Logger logger, ToolExecutionContext context, long totalMs) {
        String formattedParams = formatParams(context.params);

        logger.info(">>> [SUCCESS] Tool: {}, Params: {}, Duration: {} ms",
                context.toolName, formattedParams, totalMs);

        sendNotification(context, McpSchema.LoggingLevel.INFO,
                String.format("[SUCCESS] Completed in %d ms", totalMs));
    }

    private void logToolError(Logger logger, ToolExecutionContext context, long elapsedMs, String errorMessage) {
        String formattedParams = formatParams(context.params);

        logger.error(">>> [ERROR] Tool: {}, Params: {}, Failed after {} ms: {}",
                context.toolName, formattedParams, elapsedMs, errorMessage);

        sendNotification(context, McpSchema.LoggingLevel.ERROR,
                String.format("[ERROR] Failed after %d ms: %s", elapsedMs, errorMessage));
    }

    private void sendNotification(ToolExecutionContext context, McpSchema.LoggingLevel level, String message) {
        if (context.toolContext == null) {
            return;
        }

        McpToolUtils.getMcpExchange(context.toolContext).ifPresent(exchange ->
                exchange.loggingNotification(new McpSchema.LoggingMessageNotification(
                        level,
                        context.toolName.toLowerCase().replace('_', '-'),
                        message)));
    }

    private String formatParams(Map<?, ?> params) {
        if (params == null || params.isEmpty()) {
            return "{}";
        }

        Map<Object, String> abbreviatedParams = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : params.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String value = abbreviateValue(entry.getValue());
            abbreviatedParams.put(key, value);
        }

        try {
            String json = objectMapper.writeValueAsString(abbreviatedParams);
            return abbreviate(json, 200);
        } catch (JsonProcessingException e) {
            return formatAsSimpleString(abbreviatedParams);
        }
    }

    private String abbreviateValue(Object value) {
        if (value == null) {
            return "null";
        }

        String stringValue = value.toString();

        if (value instanceof java.util.Collection<?> collection) {
            return String.format("[%d items]", collection.size());
        }
        if (value.getClass().isArray()) {
            if (value instanceof Object[] array) {
                return String.format("[%d items]", array.length);
            }
            return "[array]";
        }

        if (stringValue.length() > 50) {
            return abbreviate(stringValue, 50);
        }

        return stringValue;
    }

    private String formatAsSimpleString(Map<Object, String> params) {
        String formatted = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "{", "}"));

        return abbreviate(formatted, 200);
    }

    public static class ToolExecutionContext {
        private final Class<?> toolClass;
        private final String toolName;
        private final ToolContext toolContext;
        private final Map<?, ?> params;

        private ToolExecutionContext(Builder builder) {
            this.toolClass = builder.toolClass;
            this.toolName = builder.toolName;
            this.toolContext = builder.toolContext;
            this.params = builder.params;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Class<?> toolClass;
            private String toolName;
            private ToolContext toolContext;
            private Map<?, ?> params;

            public Builder toolClass(Class<?> toolClass) {
                this.toolClass = toolClass;
                return this;
            }

            public Builder toolName(String toolName) {
                this.toolName = toolName;
                return this;
            }

            public Builder toolContext(ToolContext toolContext) {
                this.toolContext = toolContext;
                return this;
            }

            public Builder params(Map<?, ?> params) {
                this.params = params;
                return this;
            }

            public Builder paramToLog(String key, Object value) {
                if (this.params == null) {
                    this.params = new LinkedHashMap<>();
                }
                ((Map<Object, Object>) this.params).put(key, value);
                return this;
            }

            public ToolExecutionContext build() {
                if (toolClass == null) {
                    throw new IllegalStateException("toolClass is required");
                }
                if (toolName == null) {
                    throw new IllegalStateException("toolName is required");
                }
                return new ToolExecutionContext(this);
            }
        }
    }
}