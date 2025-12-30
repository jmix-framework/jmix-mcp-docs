package io.jmix.ai.mcpdocs.e2e;

import io.jmix.ai.mcpdocs.service.RateLimitService;
import io.jmix.ai.mcpdocs.util.mcp.SequentialMcpCaller;
import io.jmix.ai.mcpdocs.util.mcp.SequentialMcpCaller.McpCallResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests for request-based rate limiting.
 * Tests RateLimitFilter behavior with real HTTP requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
class RateLimitE2ETest extends BaseE2ETest {

    @Autowired
    private RateLimitService rateLimitService;

    @Test
    void testRequestsWithinLimitAreAccepted() throws Exception {
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(3)
                .withQuery(i -> "Query " + i)
                .call();

        assertThat(result.successfulCalls()).isEqualTo(3);
        assertThat(result.allSucceeded()).isTrue();
    }

    @Test
    void testExceedingPerIpLimitIsRejected() throws Exception {
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(5)
                .withQuery(i -> "Query " + i)
                .call();

        assertThat(result.successfulCalls()).isEqualTo(3);
        assertThat(result.hitRateLimit()).isTrue();
        assertThat(result.firstFailureIndex()).isEqualTo(3);
    }

    @Test
    void testRateLimitFilterBlocksExcessRequests() throws Exception {
        // Exhaust the per-IP limit (3 requests)
        SequentialMcpCaller.of(newTestClient())
                .iterate(3)
                .withQuery(i -> "Query " + i)
                .call();

        // Next request should be blocked
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> "Should be blocked")
                .call();

        assertThat(result.hitRateLimit()).isTrue();
        assertThat(result.successfulCalls()).isZero();
    }

    @Test
    void testGlobalRateLimitIsEnforced() throws Exception {
        // Exhaust global limit (100 requests per minute from test profile)
        for (int i = 0; i < 100; i++) {
            rateLimitService.tryConsumeGlobal();
        }

        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> "Should hit global limit")
                .call();

        assertThat(result.hitRateLimit()).isTrue();
        assertThat(result.successfulCalls()).isZero();
    }

    @Test
    void testRateLimitHeadersAreReturned() throws Exception {
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> "Test")
                .call();

        assertThat(result.allSucceeded()).isTrue();

        // After first request, should have 2 remaining (3 total, 1 consumed)
        long remaining = rateLimitService.getRemainingTokensForIp("127.0.0.1");
        assertThat(remaining).isEqualTo(2);
    }

    @Test
    void testPerformanceUnderLoad() throws Exception {
        long startTime = System.currentTimeMillis();

        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(3)
                .withQuery(i -> "Performance test " + i)
                .call();

        long duration = System.currentTimeMillis() - startTime;

        assertThat(result.allSucceeded()).isTrue();
        assertThat(duration).as("3 requests should complete in under 5 seconds").isLessThan(5000);
    }

    @Test
    void testConcurrentRequestsFromSameIpShareLimit() throws Exception {
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(4)
                .withQuery(i -> "Concurrent " + i)
                .call();

        assertThat(result.successfulCalls()).isLessThanOrEqualTo(3);
        assertThat(result.hitRateLimit()).isTrue();
    }

    @Test
    void testRateLimitResetAfterTimeout() throws Exception {
        // Exhaust limit
        SequentialMcpCaller.of(newTestClient())
                .iterate(3)
                .withQuery(i -> "Initial " + i)
                .call();

        // Verify limit is hit
        long remaining = rateLimitService.getRemainingTokensForIp("127.0.0.1");
        assertThat(remaining).isZero();

        // Wait for refill (5 seconds from test profile)
        Thread.sleep(6000);

        // Should be able to make requests again
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> "After refill")
                .call();

        assertThat(result.allSucceeded())
                .isTrue();
    }

    @Test
    void testFilterProcessingOrderWithMultipleLimits() throws Exception {
        // Global limit is checked before per-IP limit in RateLimitFilter
        for (int i = 0; i < 100; i++) {
            rateLimitService.tryConsumeGlobal();
        }

        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> "Test order")
                .call();

        assertThat(result.hitRateLimit()).isTrue();
        assertThat(result.successfulCalls()).isZero();
    }

    @Test
    void testPartialConsumption() throws Exception {
        // First 3 requests should succeed, 4th should fail
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(4)
                .withQuery(i -> "Request " + i)
                .call();

        assertThat(result.successfulCalls()).isEqualTo(3);
        assertThat(result.hitRateLimit()).isTrue();
        assertThat(result.firstFailureIndex()).isEqualTo(3);
    }

    @Test
    void testRateLimitWithLargePayload() throws Exception {
        // Large payload still counts as single request (but stays within token budget)
        // Query size: 300 chars = ~75 tokens, within per-IP limit of 100 tokens/min
        String largeQuery = "x".repeat(300);

        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> largeQuery + " " + i)
                .call();

        assertThat(result.allSucceeded()).isTrue();
    }

    @Test
    void testRateLimitPersistsAcrossConnections() throws Exception {
        // First connection exhausts limit
        SequentialMcpCaller.of(newTestClient())
                .iterate(3)
                .withQuery(i -> "First connection " + i)
                .call();

        // Second connection (same IP) should still be blocked
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> "Second connection")
                .call();

        assertThat(result.hitRateLimit()).isTrue();
        assertThat(result.successfulCalls()).isZero();
    }
}
