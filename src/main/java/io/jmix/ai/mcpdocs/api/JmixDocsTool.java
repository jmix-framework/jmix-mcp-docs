package io.jmix.ai.mcpdocs.api;

import io.jmix.ai.mcpdocs.service.JmixContentSearchService;
import io.jmix.ai.mcpdocs.service.McpToolTelemetry;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;

@Component
public class JmixDocsTool {
    public static final String JMIX_DOCS_TOOL = "search-jmix-docs";
    public static final String JMIX_DOCS_TOOL_DESCRIPTION = "Search Jmix documentation using semantic search with reranking";

    private final JmixContentSearchService jmixContentSearchService;
    private final McpToolTelemetry mcpToolTelemetry;

    public JmixDocsTool(JmixContentSearchService jmixContentSearchService,
                        McpToolTelemetry mcpToolTelemetry) {
        this.jmixContentSearchService = jmixContentSearchService;
        this.mcpToolTelemetry = mcpToolTelemetry;
    }

    @McpTool(name = JMIX_DOCS_TOOL,
            description = JMIX_DOCS_TOOL_DESCRIPTION)
    public McpSchema.CallToolResult search(
            @McpToolParam(description = "Search query for Jmix documentation")
            @NotNull String queryText
    ) {
        McpToolTelemetry.ToolExecutionContext context = McpToolTelemetry.ToolExecutionContext.builder()
                .toolClass(JmixDocsTool.class)
                .toolName(JMIX_DOCS_TOOL)
                .paramToLog("query", queryText)
                .build();

        return mcpToolTelemetry.executeWithTelemetry(
                context,
                () -> jmixContentSearchService.search(queryText)
        );
    }
}