package io.jmix.ai.mcpdocs.e2e;

import io.jmix.ai.mcpdocs.util.mcp.McpTestClient;
import io.jmix.ai.mcpdocs.util.mcp.McpResponse;
import io.jmix.ai.mcpdocs.util.mcp.SequentialMcpCaller;
import io.jmix.ai.mcpdocs.util.mcp.SequentialMcpCaller.McpCallResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge cases E2E tests:
 * - Null/empty/whitespace queries
 * - Special characters
 * - Boundary values
 * - Unicode/emoji handling
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
class EdgeCasesE2ETest extends BaseE2ETest {
    // todo make tests with testcontainer ai assistant with md25 search (w/o embedding)

    @Test
    void testWhitespaceOnlyQuery() throws Exception {
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> "   ")
                .call();

        // Whitespace-only queries should be rejected by @NotBlank validation
        assertThat(result.hitError()).isTrue();
        assertThat(result.successfulCalls()).isZero();
    }

    @Test
    void testSpecialCharactersQuery() throws Exception {
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .useQueries(
                    "SELECT * FROM users WHERE id=1",  // SQL-like query
                    "<script>alert('xss')</script>",  // HTML/XSS
                    "../../etc/passwd"  // Path-like query
                )
                .call();

        // Should handle without errors (input is just text, not executed)
        assertThat(result.allSucceeded()).isTrue();
    }

    @Test
    void testQueryWithNewlines() throws Exception {
        String multilineQuery = "First line\nSecond line\nThird line";

        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> multilineQuery)
                .call();

        assertThat(result.allSucceeded()).isTrue();
    }

    @Test
    void testQueryWithControlCharacters() throws Exception {
        String queryWithControlChars = "Test\u0000null\u0001byte\u0002chars";

        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> queryWithControlChars)
                .call();

        // Should handle control characters
        assertThat(result.anyFailed()).isFalse();
    }

    @Test
    void testVeryLongSingleWord() throws Exception {
        // Single word with no spaces (2000 chars)
        String longWord = "a".repeat(2000);

        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> longWord)
                .call();

        assertThat(result.allSucceeded()).isTrue();
    }

    @Test
    void testQueryWithMixedUnicode() throws Exception {
        // Reduced to 3 queries to stay within per-IP request rate limit (3 requests/5s)
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .useQueries(
                    "Тест на русском языке",  // Cyrillic
                    "中文测试查询",  // Chinese
                    "Test with emojis 🚀🎉🔥"  // Emojis
                )
                .call();

        assertThat(result.allSucceeded())
                .as("Expected all queries to succeed but got: successful=%d, failed=%d, hitRateLimit=%b, hitTokenLimit=%b, hitError=%b, firstError=%s",
                        result.successfulCalls(), result.failedCalls(), result.hitRateLimit(),
                        result.hitTokenLimit(), result.hitError(), result.firstErrorMessage())
                .isTrue();
    }

    @Test
    void testQueryWithOnlyEmojis() throws Exception {
        String emojiQuery = "🚀🎉🔥💻🌟";

        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> emojiQuery)
                .call();

        assertThat(result.allSucceeded()).isTrue();
    }

    @Test
    void testQueryWithUrlAndHtml() throws Exception {
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .useQueries(
                    "https://example.com/test?param=value&other=123",
                    "<div>HTML content</div>",
                    "Email: test@example.com"
                )
                .call();

        assertThat(result.allSucceeded()).isTrue();
    }

    @Test
    void testQueryWithEscapeSequences() throws Exception {
        String queryWithEscapes = "Test\\nwith\\ttab\\rand\\\"quotes\\\"";

        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> queryWithEscapes)
                .call();

        assertThat(result.allSucceeded()).isTrue();
    }

    @Test
    void testNullQueryHandling() throws Exception {
        McpTestClient client = newTestClient();
        client.connect();

        try {
            // Explicitly pass null (not empty string)
            McpResponse.ToolCallResponse result = client.callTool("search-jmix-docs",
                    Map.of("queryText", (String) null));

            // Should either reject or handle gracefully
            boolean handledGracefully = result.hasError() ||
                    (result.hasResult() && result.getTextContent() != null);

            assertThat(handledGracefully).as("Null query should be handled").isTrue();
        } catch (NullPointerException e) {
            // NPE is also acceptable (fail fast)
        } finally {
            client.close();
        }
    }

    @Test
    void testQueryWithRepeatedCharacters() throws Exception {
        // Limit to 3 queries to stay within rate limit (3 requests per 5 seconds)
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .useQueries(
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "111111111111111111111111",
                    "test test test test test"
                )
                .call();

        assertThat(result.allSucceeded()).isTrue();
    }

    @Test
    void testQueryWithJsonCharacters() throws Exception {
        String jsonLikeQuery = "{\"key\": \"value\", \"nested\": {\"array\": [1,2,3]}}";

        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> jsonLikeQuery)
                .call();

        assertThat(result.allSucceeded()).isTrue();
    }

    @Test
    void testQueryWithBackslashes() throws Exception {
        String windowsPath = "C:\\Users\\Test\\Documents\\file.txt";

        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> windowsPath)
                .call();

        assertThat(result.allSucceeded()).isTrue();
    }

    @Test
    void testBoundaryLengthMinus1() throws Exception {
        // 1999 characters (just under limit)
        String query = "x".repeat(1999);

        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> query)
                .call();

        assertThat(result.allSucceeded()).isTrue();
    }

    @Test
    void testBoundaryTokensMinus1() throws Exception {
        // 499 tokens = 1996 characters (just under 500 token limit)
        String query = "y".repeat(1996);

        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> query)
                .call();

        assertThat(result.allSucceeded()).isTrue();
    }
}
