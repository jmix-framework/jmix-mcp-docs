package io.jmix.ai.mcpdocs.e2e;

import io.jmix.ai.mcpdocs.util.mcp.McpResponse;
import io.jmix.ai.mcpdocs.util.mcp.McpSseTestClient;
import io.jmix.ai.mcpdocs.util.mcp.McpTestClient;
import io.jmix.ai.mcpdocs.util.data.MockMcpResponseProvider;
import io.jmix.ai.mcpdocs.util.mcp.SequentialMcpCaller;
import io.jmix.ai.mcpdocs.util.mcp.SequentialMcpCaller.McpCallResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * E2E tests for MCP protocol implementation.
 * Tests the full flow: Streamable HTTP connection -> initialize -> tools/list -> tools/call
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "rate-limit.ip.capacity=20"  // Higher limit for E2E tests with multiple connections
})
class McpProtocolE2ETest extends BaseE2ETest {


    @Test
    void testFullMcpProtocolFlow() throws Exception {
        McpTestClient client = newTestClient();

        try (client) {
            client.connect();
            // List available tools
            McpResponse.ToolListResponse toolsList = client.listTools();
            assertThat(toolsList.hasResult()).isTrue();
            assertThat(toolsList.getTools()).isNotEmpty();

            // Verify our tool is present
            McpResponse.Tool searchTool = toolsList.findTool("search-jmix-docs");
            assertThat(searchTool).isNotNull();
            assertThat(searchTool.hasDescription()).isTrue();
            assertThat(searchTool.getDescription()).contains("Search Jmix documentation");

            // Call the search tool
            McpResponse.ToolCallResponse result = client.callTool("search-jmix-docs",
                    Map.of("queryText", "Jmix DataGrid"));

            // Verify response structure
            assertThat(result.hasResult()).isTrue();
            assertThat(result.getContent()).isNotEmpty();
            assertThat(result.getContent().get(0).getType()).isEqualTo("text");

            // Verify the mocked response is returned
            assertThat(result.getTextContent()).contains("DataGrid");
        }
    }

    @Test
    void testMultipleSequentialToolCalls() throws Exception {
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(3)
                .withQuery(i -> "Test query " + i)
                .call();

        assertThat(result.allSucceeded()).isTrue();
    }

    @Test
    void testToolCallWithDifferentArguments() throws Exception {
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .useQueries("Entity listeners", "Security constraints", "Data model")
                .call();

        assertThat(result.allSucceeded()).isTrue();
    }

    @Test
    void testErrorHandlingWhenBackendServiceFails() throws Exception {
        when(searchService.search(anyString()))
                .thenThrow(new RuntimeException("Backend service unavailable"));

        McpTestClient client = newTestClient();

        try (client) {
            client.connect();
            McpResponse.ToolCallResponse result = client.callTool("search-jmix-docs",
                    Map.of("queryText", "Test query"));

            // Error should be returned in response (not thrown)
            boolean hasError = result.hasError() ||
                    (result.hasResult() && result.getTextContent() != null &&
                            (result.getTextContent().contains("Error") ||
                                    result.getTextContent().contains("error") ||
                                    result.getTextContent().contains("Exception")));

            assertThat(hasError).as("Backend error should be returned in response").isTrue();
        }
    }

    @Test
    void testMcpInitializationHandshake() throws Exception {
        McpTestClient client = newTestClient();

        try (client) {
            client.connect();
            McpResponse.ToolListResponse toolsList = client.listTools();

            // MCP initialization should succeed
            assertThat(toolsList.hasResult()).isTrue();
            assertThat(toolsList.getTools()).isNotEmpty();
        }
    }

    @Test
    void testConnectionEstablishment() throws Exception {
        McpTestClient client = newTestClient();
        // Connection should be established without errors

        try (client) {
            client.connect();
            // Verify connection by making a request
            McpResponse.ToolListResponse toolsList = client.listTools();
            assertThat(toolsList.hasResult()).isTrue();
        }
    }

    @Test
    void testSseBackwardCompatibility() throws Exception {
        McpSseTestClient sseClient = newSseTestClient();

        try (sseClient) {
            sseClient.connect();

            McpResponse.ToolListResponse toolsList = sseClient.listTools();
            assertThat(toolsList.hasResult()).isTrue();
            assertThat(toolsList.getTools()).isNotEmpty();
            assertThat(toolsList.findTool("search-jmix-docs")).isNotNull();

            McpResponse.ToolCallResponse result = sseClient.callTool("search-jmix-docs",
                    Map.of("queryText", "Jmix DataGrid"));

            assertThat(result.hasResult()).isTrue();
            assertThat(result.getTextContent()).contains("DataGrid");
        }
    }

    @Test
    void testInvalidToolName() throws Exception {
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .tool("non-existent-tool")
                .iterate(1)
                .withQuery(i -> "Test")
                .call();

        assertThat(result.hitError()).isTrue();
        assertThat(result.successfulCalls()).isZero();
    }

    @Test
    void testMissingRequiredArgument() throws Exception {
        McpTestClient client = newTestClient();

        try (client) {
            client.connect();
            // Call with empty arguments (missing queryText)
            McpResponse.ToolCallResponse result = client.callTool("search-jmix-docs", Map.of());

            boolean hasError = result.hasError() ||
                    (result.hasResult() && result.getTextContent() != null &&
                            result.getTextContent().contains("required"));

            assertThat(hasError).as("Missing required argument should fail").isTrue();
        }
    }

    @Test
    void testInvalidArgumentType() throws Exception {
        McpTestClient client = newTestClient();

        try (client) {
            client.connect();

            // Pass integer instead of string
            // Spring may auto-convert this, so we just verify server doesn't crash
            McpResponse.ToolCallResponse result = client.callTool("search-jmix-docs",
                    Map.of("queryText", 12345));

            // Either it works (auto-converted) or returns error - both acceptable
            assertThat(result).isNotNull();
        }
    }

    @Test
    void testEmptyToolCallArguments() throws Exception {
        McpTestClient client = newTestClient();

        try (client) {
            client.connect();
            McpResponse.ToolCallResponse result = client.callTool("search-jmix-docs", Map.of());

            boolean hasError = result.hasError() ||
                    (result.hasResult() && result.getTextContent() != null &&
                            (result.getTextContent().contains("Error") ||
                                    result.getTextContent().contains("required")));

            assertThat(hasError).as("Empty arguments should fail").isTrue();
        }
    }

    @Test
    void testVeryLargeResponseHandling() throws Exception {
        // Mock returns very large response
        String largeResponse = "Large response data: " + "x".repeat(10000);
        when(searchService.search(anyString())).thenReturn(largeResponse);

        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> "Test large response")
                .call();

        assertThat(result.allSucceeded()).isTrue();
    }

    @Test
    void testMultipleToolListRequests() throws Exception {
        McpTestClient client = newTestClient();

        try (client) {
            client.connect();
            // List tools twice (stay within rate limit of 3 requests per 5 seconds)
            // Note: connect() makes requests too (initialize + initialized notification)
            McpResponse.ToolListResponse first = client.listTools();

            // Wait to avoid rate limit (connect() already used 2 requests)
            Thread.sleep(6000);

            McpResponse.ToolListResponse second = client.listTools();

            // Compare tool names (Tool class doesn't implement equals())
            assertThat(first.getTools()).hasSameSizeAs(second.getTools());
            assertThat(first.getTools()).isNotEmpty();
            assertThat(first.getTools().get(0).getName())
                    .isEqualTo(second.getTools().get(0).getName());
        }
    }
}
