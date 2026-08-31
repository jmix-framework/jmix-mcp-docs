package io.jmix.ai.mcpdocs.api;

import io.jmix.ai.mcpdocs.service.JmixContentSearchService;
import io.jmix.ai.mcpdocs.service.McpToolTelemetry;
import io.jmix.ai.mcpdocs.service.SearchOptions;
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
    public static final String JMIX_DOCS_TOOL_DESCRIPTION = """
            Search Jmix documentation using semantic search with reranking. \
            Returns relevance-ordered snippets with title, source URL and content.""";

    public static final String QUERY_TEXT_PARAM_DESCRIPTION =
            "Search query for Jmix documentation";
    public static final String JMIX_VERSION_PARAM_DESCRIPTION =
            "Jmix major version to search: 'v2' or 'v3'. Omit to search the current Jmix release.";
    public static final String MAX_RESULTS_PARAM_DESCRIPTION =
            "Maximum total number of returned snippets, 1-50. Omit for the server default.";
    public static final String TOKENS_PARAM_DESCRIPTION = """
            Approximate response token budget, 1-100000; the most relevant snippets are kept. \
            Omit to return the full result set.""";

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
            @McpToolParam(description = QUERY_TEXT_PARAM_DESCRIPTION) String queryText,
            @McpToolParam(description = JMIX_VERSION_PARAM_DESCRIPTION, required = false) String jmixVersion,
            @McpToolParam(description = MAX_RESULTS_PARAM_DESCRIPTION, required = false) Integer maxResults,
            @McpToolParam(description = TOKENS_PARAM_DESCRIPTION, required = false) Integer tokens
    ) {
        SearchOptions options = new SearchOptions(jmixVersion, maxResults, tokens);

        // Validate request (rate limits + input validation + token budget);
        // search options are validated by the backend, its rejection is proxied as a readable error
        Optional<McpError> validationError = requestValidator.validateAll(queryText);
        if (validationError.isPresent()) {
            return requestValidator.toErrorResponse(validationError.get());
        }

        // Execute search with telemetry
        McpToolTelemetry.ToolExecutionContext.Builder contextBuilder = McpToolTelemetry.ToolExecutionContext.builder()
                .toolClass(JmixDocsTool.class)
                .toolName(JMIX_DOCS_TOOL);

        if (jmixVersion != null) {
            contextBuilder.paramToLog("jmixVersion", jmixVersion);
        }
        if (maxResults != null) {
            contextBuilder.paramToLog("maxResults", maxResults);
        }
        if (tokens != null) {
            contextBuilder.paramToLog("tokens", tokens);
        }

        return mcpToolTelemetry.executeWithTelemetry(
                contextBuilder.build(),
                () -> jmixContentSearchService.search(queryText, options)
        );
    }
}
