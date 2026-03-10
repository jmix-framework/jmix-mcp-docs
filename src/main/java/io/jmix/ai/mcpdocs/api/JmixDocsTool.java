package io.jmix.ai.mcpdocs.api;

import io.jmix.ai.mcpdocs.service.JmixContentSearchService;
import io.jmix.ai.mcpdocs.service.McpToolTelemetry;
import io.jmix.ai.mcpdocs.validation.McpError;
import io.jmix.ai.mcpdocs.validation.McpRequestValidator;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JmixDocsTool {
    public static final String JMIX_DOCS_TOOL = "search-jmix-docs";
    public static final String JMIX_DOCS_TOOL_DESCRIPTION = "Search Jmix documentation using semantic search with reranking";

    private final JmixContentSearchService jmixContentSearchService;
    private final McpToolTelemetry mcpToolTelemetry;
    private final McpRequestValidator requestValidator;

    public JmixDocsTool(JmixContentSearchService jmixContentSearchService,
                        McpToolTelemetry mcpToolTelemetry,
                        McpRequestValidator requestValidator) {
        this.jmixContentSearchService = jmixContentSearchService;
        this.mcpToolTelemetry = mcpToolTelemetry;
        this.requestValidator = requestValidator;
    }

    @SuppressWarnings("UnusedReturnValue")
    @McpTool(name = JMIX_DOCS_TOOL,
            description = JMIX_DOCS_TOOL_DESCRIPTION)
    public McpSchema.CallToolResult search(
            @McpToolParam(description = "Search query for Jmix documentation")
            String queryText
    ) {
        // Validate request (rate limits + input validation + token budget)
        Optional<McpError> validationError = requestValidator.validateAll(queryText);
        if (validationError.isPresent()) {
            return requestValidator.toErrorResponse(validationError.get());
        }

        // Execute search with telemetry
        McpToolTelemetry.ToolExecutionContext context = McpToolTelemetry.ToolExecutionContext.builder()
                .toolClass(JmixDocsTool.class)
                .toolName(JMIX_DOCS_TOOL)
                .build();

        return mcpToolTelemetry.executeWithTelemetry(
                context,
                () -> jmixContentSearchService.search(queryText)
        );
    }
}
