package io.jmix.ai.mcpdocs.api;

import io.jmix.ai.mcpdocs.service.JmixDocsResearchService;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolListChanged;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JmixDocs {
    public static final String JMIX_DOCS_TOOL = "search-jmix-docs";
    public static final String JMIX_DOCS_TOOL_DESCRIPTION = "Search Jmix documentation using semantic search with reranking";

    public static final double SIMILARITY_THRESHOLD = 0.0;
    public static final int TOP_K = 10;
    public static final double MIN_SCORE = 0.5;
    public static final int TOP_RERANKED = 3;
    public static final double MIN_RERANKED_SCORE = 0.2;
    public static final String NO_RESULTS_MESSAGE = "No documentation found for the query. Try rephrasing your query or using another tool.";

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final JmixDocsResearchService docsResearchService;

    public JmixDocs(JmixDocsResearchService docsResearchService) {
        this.docsResearchService = docsResearchService;
    }

    @McpTool(name = JMIX_DOCS_TOOL, description = JMIX_DOCS_TOOL_DESCRIPTION)
    public McpSchema.CallToolResult search(
            @McpToolParam(description = "Search query for Jmix documentation")
            @NotNull String queryText,
            ToolContext toolContext
    ) {
        logger.info(">>> Using {}: {}", JMIX_DOCS_TOOL,
                StringUtils.abbreviate(queryText, 120));

        McpToolUtils.getMcpExchange(toolContext).ifPresent(exchange ->
                exchange.loggingNotification(new McpSchema.LoggingMessageNotification(
                        McpSchema.LoggingLevel.INFO, "search-jmix-docs",
                        "Searching docs for: " + queryText)));

        List<String> results = docsResearchService.searchForJmixDocs(queryText);

        return McpSchema.CallToolResult.builder()
                .addTextContent(String.join("\n---\n\n", results))
                .build();
    }
}

