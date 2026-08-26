package io.jmix.ai.mcpdocs.e2e;

import io.jmix.ai.mcpdocs.service.JmixContentSearchService;
import io.jmix.ai.mcpdocs.util.data.MockMcpResponseProvider;
import io.jmix.ai.mcpdocs.util.mcp.McpSseTestClient;
import io.jmix.ai.mcpdocs.util.mcp.McpTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;


public abstract class BaseE2ETest {

    @LocalServerPort
    protected int port;

    @MockitoBean
    protected JmixContentSearchService searchService;

    @BeforeEach
    void setUpBaseMocks() {
        when(searchService.search(anyString(), any()))
                .thenReturn(MockMcpResponseProvider.JMIX_DOCS_SEARCH_RESPONSE);
    }

    /**
     * Create new Streamable HTTP MCP test client.
     * Client is NOT connected - SequentialMcpCaller will handle connection.
     */
    protected McpTestClient newTestClient() {
        String baseUrl = "http://localhost:" + port;
        return new McpTestClient(baseUrl);
    }

    /**
     * Create new SSE MCP test client for backward compatibility testing.
     * Client is NOT connected - caller must invoke connect().
     */
    protected McpSseTestClient newSseTestClient() {
        String baseUrl = "http://localhost:" + port;
        return new McpSseTestClient(baseUrl);
    }
}
