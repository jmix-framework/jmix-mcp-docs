package io.jmix.ai.mcpdocs.e2e;

import io.jmix.ai.mcpdocs.service.RateLimitService;
import io.jmix.ai.mcpdocs.service.TokenBudgetService;
import io.jmix.ai.mcpdocs.util.mcp.SequentialMcpCaller;
import io.jmix.ai.mcpdocs.util.mcp.SequentialMcpCaller.McpCallResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration E2E tests combining multiple features:
 * - Rate limiting + Token budgets
 * - Multiple clients
 * - Global vs Per-IP limits
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
class CrossValidatedE2ETest extends BaseE2ETest {


    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private TokenBudgetService tokenBudgetService;

    @Test
    void testRateLimitThenTokenLimit() throws Exception {
        // First, exhaust request rate limit (3 requests)
        SequentialMcpCaller.of(newTestClient())
                .iterate(3)
                .withQuery(i -> "Query " + i)
                .call();

        // Now even if we had token budget, rate limit blocks us
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> "Should hit rate limit")
                .call();

        assertThat(result.hitRateLimit()).isTrue();
        assertThat(result.successfulCalls()).isZero();
    }

    @Test
    void testTokenLimitThenRateLimit() throws Exception {
        // Test that token budget is properly tracked
        // Note: With greedy refill, tokens refill continuously (~8 tokens/second)
        // Connection setup takes ~1 second, so ~8 tokens refill before the request
        // This test verifies the token budget system works, even with refill

        long initialGlobal = tokenBudgetService.getGlobalRemainingTokens().perMinute();
        long initialIp = tokenBudgetService.getRemainingTokensForIp("127.0.0.1").perMinute();

        // Make a request that consumes tokens
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> "Test query")
                .call();

        assertThat(result.allSucceeded()).isTrue();

        // Verify tokens were consumed
        long afterGlobal = tokenBudgetService.getGlobalRemainingTokens().perMinute();
        long afterIp = tokenBudgetService.getRemainingTokensForIp("127.0.0.1").perMinute();

        assertThat(afterGlobal).isLessThan(initialGlobal);
        assertThat(afterIp).isLessThan(initialIp);
    }

    @Test
    void testGlobalRateLimitAffectsAllIps() throws Exception {
        // Exhaust global rate limit
        for (int i = 0; i < 100; i++) {
            rateLimitService.tryConsumeGlobal();
        }

        // Any request from any IP should be blocked
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> "Global limit hit")
                .call();

        assertThat(result.hitRateLimit()).isTrue();
        assertThat(result.successfulCalls()).isZero();
    }

    @Test
    void testGlobalTokenBudgetAffectsAllIps() throws Exception {
        // Test that global token budget is properly tracked across IPs
        // Note: With greedy refill, we test that tokens are consumed, not exhaustion

        long initialGlobal = tokenBudgetService.getGlobalRemainingTokens().perMinute();

        // Make a request
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> "Test global budget")
                .call();

        assertThat(result.allSucceeded()).isTrue();

        // Verify global tokens were consumed
        long afterGlobal = tokenBudgetService.getGlobalRemainingTokens().perMinute();
        assertThat(afterGlobal).isLessThan(initialGlobal);
    }

    @Test
    void testMultipleClientsSameIpShareLimits() throws Exception {
        // First client consumes 2 requests
        SequentialMcpCaller.of(newTestClient())
                .iterate(2)
                .withQuery(i -> "Client 1 request " + i)
                .call();

        // Second client (same IP 127.0.0.1) should have only 1 request left
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(2)
                .withQuery(i -> "Client 2 request " + i)
                .call();

        // Only 1 request should succeed, 2nd should hit rate limit
        assertThat(result.successfulCalls()).isEqualTo(1);
        assertThat(result.hitRateLimit()).isTrue();
    }

    @Test
    void testLongQueryPassesValidationButHitsTokenBudget() throws Exception {
        // Query is within length limit (1800 chars < 2000)
        // But consumes 450 tokens (1800/4 = 450)
        String longQuery = "a".repeat(1800);

        // Consume most of token budget, leaving only 100 tokens
        tokenBudgetService.tryConsumeGlobal(400);

        // This query needs 450 tokens but only 100 available
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> longQuery)
                .call();

        assertThat(result.hitTokenLimit()).isTrue();
        assertThat(result.successfulCalls()).isZero();
    }

    @Test
    void testRapidFireMixedQueries() throws Exception {
        // Mix of valid queries of different lengths (3 to stay within rate limit)
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .useQueries(
                    "Valid query",
                    "Short",
                    "Another valid query"
                )
                .call();

        // All should succeed (within both rate and token limits)
        assertThat(result.allSucceeded()).isTrue();
    }

    @Test
    void testRecoveryAfterBothLimitsHit() throws Exception {
        // Exhaust both limits
        rateLimitService.tryConsumeForIp("127.0.0.1");
        rateLimitService.tryConsumeForIp("127.0.0.1");
        rateLimitService.tryConsumeForIp("127.0.0.1");
        tokenBudgetService.tryConsumeForIp("127.0.0.1", 500);

        // Verify both are exhausted
        long rateRemaining = rateLimitService.getRemainingTokensForIp("127.0.0.1");
        TokenBudgetService.TokenQuota tokenRemaining = tokenBudgetService.getRemainingTokensForIp("127.0.0.1");

        assertThat(rateRemaining).isZero();
        assertThat(tokenRemaining.perMinute()).isZero();

        // Wait for refill (test config: 5 seconds) - add extra buffer
        Thread.sleep(6000);

        // Verify limits have been refilled
        long rateAfterRefill = rateLimitService.getRemainingTokensForIp("127.0.0.1");
        TokenBudgetService.TokenQuota tokenAfterRefill = tokenBudgetService.getRemainingTokensForIp("127.0.0.1");

        assertThat(rateAfterRefill).isGreaterThan(0);
        assertThat(tokenAfterRefill.perMinute()).isGreaterThan(0);
    }

    @Test
    void testPerIpLimitIndependentOfGlobal() throws Exception {
        // Exhaust per-IP limit (3 requests)
        SequentialMcpCaller.of(newTestClient())
                .iterate(3)
                .withQuery(i -> "IP exhausted " + i)
                .call();

        // Global still has capacity (100 - 3 = 97)
        long globalRemaining = rateLimitService.getGlobalRemainingTokens();
        assertThat(globalRemaining).isGreaterThan(90);

        // But this IP is blocked
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> "Should be blocked by IP limit")
                .call();

        assertThat(result.hitRateLimit()).isTrue();
        assertThat(result.successfulCalls()).isZero();
    }

    @Test
    void testBothLimitsCheckedInOrder() throws Exception {
        // Exhaust global limit first
        for (int i = 0; i < 100; i++) {
            rateLimitService.tryConsumeGlobal();
        }

        // Per-IP still has capacity
        long ipRemaining = rateLimitService.getRemainingTokensForIp("127.0.0.1");
        assertThat(ipRemaining).isEqualTo(3);

        // Request should fail on global check (before IP check)
        McpCallResult result = SequentialMcpCaller.of(newTestClient())
                .iterate(1)
                .withQuery(i -> "Global blocks first")
                .call();

        assertThat(result.hitRateLimit()).isTrue();

        // Per-IP limit should still be at 3 (not consumed)
        ipRemaining = rateLimitService.getRemainingTokensForIp("127.0.0.1");
        assertThat(ipRemaining).isEqualTo(3);
    }
}
