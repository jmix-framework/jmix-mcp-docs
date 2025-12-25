package io.jmix.ai.mcpdocs.util.mcp;

import java.util.Map;
import java.util.function.Function;

/**
 * Fluent builder for sequential MCP tool calls.
 * Simplifies testing of rate limiting and token budgets.
 *
 * Example:
 * <pre>
 * McpCallResult result = SequentialMcpCaller.of(client)
 *     .tool("search-jmix-docs")
 *     .iterate(5)
 *     .withQuery(i -> "Query " + i)
 *     .call();
 *
 * assertThat(result.successfulCalls()).isEqualTo(3);
 * assertThat(result.hitRateLimit()).isTrue();
 * </pre>
 */
public class SequentialMcpCaller {

    private final McpTestClient client;
    private String toolName = "search-jmix-docs";
    private int iterations = 1;
    private Function<Integer, String> queryGenerator = i -> "Test query " + i;
    private Function<Integer, Map<String, Object>> argsGenerator = null;

    private SequentialMcpCaller(McpTestClient client) {
        this.client = client;
    }

    public static SequentialMcpCaller of(McpTestClient client) {
        return new SequentialMcpCaller(client);
    }

    public SequentialMcpCaller tool(String toolName) {
        this.toolName = toolName;
        return this;
    }

    public SequentialMcpCaller iterate(int times) {
        this.iterations = times;
        return this;
    }

    public SequentialMcpCaller withQuery(Function<Integer, String> queryGenerator) {
        this.queryGenerator = queryGenerator;
        return this;
    }

    public SequentialMcpCaller useQueries(String... queries) {
        this.iterations = queries.length;
        this.queryGenerator = i -> queries[i];
        return this;
    }

    public SequentialMcpCaller withArgs(Function<Integer, Map<String, Object>> argsGenerator) {
        this.argsGenerator = argsGenerator;
        return this;
    }

    public McpCallResult call() throws Exception {
        int successfulCalls = 0;
        int failedCalls = 0;
        boolean hitRateLimit = false;
        boolean hitTokenLimit = false;
        boolean hitError = false;
        String firstErrorMessage = null;
        Integer firstFailureIndex = null;

        // Auto-connect client
        client.connect();

        try {
            for (int i = 0; i < iterations; i++) {
                try {
                    Map<String, Object> args;
                    if (argsGenerator != null) {
                        args = argsGenerator.apply(i);
                    } else {
                        args = Map.of("queryText", queryGenerator.apply(i));
                    }

                    McpResponse.ToolCallResponse response = client.callTool(toolName, args);

                String content = response.getTextContent();
                String contentLower = content != null ? content.toLowerCase() : "";

                // Check if this is an error response
                boolean isError = response.hasError() ||
                                 contentLower.startsWith("rate limit exceeded:") ||
                                 contentLower.startsWith("token budget exceeded:") ||
                                 contentLower.startsWith("validation error:");

                if (isError) {
                    failedCalls++;

                    if (contentLower.contains("rate limit")) {
                        hitRateLimit = true;
                        if (firstFailureIndex == null) {
                            firstFailureIndex = i;
                            firstErrorMessage = "Rate limit exceeded";
                        }
                    } else if (contentLower.contains("token budget") || contentLower.contains("budget exceeded")) {
                        hitTokenLimit = true;
                        if (firstFailureIndex == null) {
                            firstFailureIndex = i;
                            firstErrorMessage = "Token budget exceeded";
                        }
                    } else {
                        hitError = true;
                        if (firstFailureIndex == null) {
                            firstFailureIndex = i;
                            firstErrorMessage = response.hasError() ? "Response has error field" : "Error in response content";
                        }
                    }
                } else {
                    successfulCalls++;
                }

                // Stop on first failure to match test expectations
                if (failedCalls > 0) {
                    break;
                }
            } catch (Exception e) {
                failedCalls++;
                if (firstErrorMessage == null) {
                    firstErrorMessage = e.getMessage();
                    firstFailureIndex = i;
                }
                if (e.getMessage() != null) {
                    if (e.getMessage().contains("Rate limit")) {
                        hitRateLimit = true;
                    } else if (e.getMessage().contains("token budget")) {
                        hitTokenLimit = true;
                    } else {
                        hitError = true;
                    }
                }
                break;
            }
        }

            return new McpCallResult(
                    successfulCalls,
                    failedCalls,
                    hitRateLimit,
                    hitTokenLimit,
                    hitError,
                    firstErrorMessage,
                    firstFailureIndex,
                    iterations
            );
        } finally {
            // Auto-close client
            client.close();
        }
    }

    public static class McpCallResult {
        private final int successfulCalls;
        private final int failedCalls;
        private final boolean hitRateLimit;
        private final boolean hitTokenLimit;
        private final boolean hitError;
        private final String firstErrorMessage;
        private final Integer firstFailureIndex;
        private final int totalAttempts;

        McpCallResult(int successfulCalls, int failedCalls, boolean hitRateLimit,
                     boolean hitTokenLimit, boolean hitError, String firstErrorMessage,
                     Integer firstFailureIndex, int totalAttempts) {
            this.successfulCalls = successfulCalls;
            this.failedCalls = failedCalls;
            this.hitRateLimit = hitRateLimit;
            this.hitTokenLimit = hitTokenLimit;
            this.hitError = hitError;
            this.firstErrorMessage = firstErrorMessage;
            this.firstFailureIndex = firstFailureIndex;
            this.totalAttempts = totalAttempts;
        }

        public int successfulCalls() {
            return successfulCalls;
        }

        public int failedCalls() {
            return failedCalls;
        }

        public boolean hitRateLimit() {
            return hitRateLimit;
        }

        public boolean hitTokenLimit() {
            return hitTokenLimit;
        }

        public boolean hitError() {
            return hitError;
        }

        public boolean hitAnyLimit() {
            return hitRateLimit || hitTokenLimit;
        }

        public String firstErrorMessage() {
            return firstErrorMessage;
        }

        public Integer firstFailureIndex() {
            return firstFailureIndex;
        }

        public int totalAttempts() {
            return totalAttempts;
        }

        public boolean allSucceeded() {
            return successfulCalls == totalAttempts;
        }

        public boolean anyFailed() {
            return failedCalls > 0;
        }
    }
}
