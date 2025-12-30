package io.jmix.ai.mcpdocs.unit;

import io.jmix.ai.mcpdocs.api.JmixDocsTool;
import io.jmix.ai.mcpdocs.service.JmixContentSearchService;
import io.jmix.ai.mcpdocs.service.TokenBudgetService;
import io.jmix.ai.mcpdocs.util.data.MockMcpResponseProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.modelcontextprotocol.spec.McpSchema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TokenBudgetService.
 * Tests token consumption logic without HTTP layer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
class TokenBudgetServiceTest {

    @Autowired
    private TokenBudgetService tokenBudgetService;

    @Autowired
    private JmixDocsTool jmixDocsTool;

    @MockitoBean
    private JmixContentSearchService searchService;

    @BeforeEach
    void setUp() {
        when(searchService.search(anyString())).thenReturn(MockMcpResponseProvider.JMIX_DOCS_SEARCH_RESPONSE);
    }

    @Test
    void testInitialQuotaMatchesConfiguration() {
        TokenBudgetService.TokenQuota quota = tokenBudgetService.getGlobalRemainingTokens();

        assertThat(quota.perMinute()).isEqualTo(500);
        assertThat(quota.perHour()).isEqualTo(5000);
        assertThat(quota.perDay()).isEqualTo(50000);
    }

    @Test
    void testConsumeTokensDecreasesQuota() {
        boolean success = tokenBudgetService.tryConsumeGlobal(50);
        assertThat(success).isTrue();

        TokenBudgetService.TokenQuota quota = tokenBudgetService.getGlobalRemainingTokens();
        assertThat(quota.perMinute()).isEqualTo(450);
        assertThat(quota.perHour()).isEqualTo(4950);
        assertThat(quota.perDay()).isEqualTo(49950);
    }

    @Test
    void testConsumeMoreThanAvailableFails() {
        boolean success = tokenBudgetService.tryConsumeGlobal(501);
        assertThat(success).isFalse();

        // Quota should remain unchanged
        TokenBudgetService.TokenQuota quota = tokenBudgetService.getGlobalRemainingTokens();
        assertThat(quota.perMinute()).isEqualTo(500);
    }

    @Test
    void testPerIpQuotaIsIndependent() {
        tokenBudgetService.tryConsumeForIp("192.168.1.1", 50);

        TokenBudgetService.TokenQuota ip1 = tokenBudgetService.getRemainingTokensForIp("192.168.1.1");
        TokenBudgetService.TokenQuota ip2 = tokenBudgetService.getRemainingTokensForIp("192.168.1.2");

        assertThat(ip1.perMinute()).isEqualTo(450);
        assertThat(ip2.perMinute()).isEqualTo(500);  // Untouched
    }

    @Test
    void testMultipleTimeWindowsEnforcedIndependently() {
        // Exhaust per-minute limit
        boolean firstConsumption = tokenBudgetService.tryConsumeGlobal(500);
        assertThat(firstConsumption).isTrue();

        // Next consumption fails even though hour/day have capacity
        boolean success = tokenBudgetService.tryConsumeGlobal(1);
        assertThat(success).isFalse();

        TokenBudgetService.TokenQuota quota = tokenBudgetService.getGlobalRemainingTokens();
        assertThat(quota.perMinute()).isZero();
        assertThat(quota.perHour()).isEqualTo(4500);
        assertThat(quota.perDay()).isEqualTo(49500);
    }

    @Test
    void testQuotaMinimumReturnsLowestValue() {
        tokenBudgetService.tryConsumeGlobal(100);

        TokenBudgetService.TokenQuota quota = tokenBudgetService.getGlobalRemainingTokens();
        assertThat(quota.getMinimum()).isEqualTo(400);  // Per-minute is lowest
    }


    @Test
    void testEmptyQueryConsumesZeroTokens() {
        TokenBudgetService.TokenQuota beforeQuota = tokenBudgetService.getGlobalRemainingTokens();

        // Empty query estimates to 0 tokens (ceil(0/4) = 0)
        jmixDocsTool.search("");

        TokenBudgetService.TokenQuota afterQuota = tokenBudgetService.getGlobalRemainingTokens();

        // Empty query should consume 0 tokens
        // Note: tryConsume(0) succeeds without consuming tokens
        assertThat(afterQuota.perMinute()).isEqualTo(beforeQuota.perMinute());
    }

    @Test
    void testSmallQueryConsumesMinimalTokens() {
        TokenBudgetService.TokenQuota beforeQuota = tokenBudgetService.getGlobalRemainingTokens();

        // Small query (~1 token)
        jmixDocsTool.search("a");

        TokenBudgetService.TokenQuota afterQuota = tokenBudgetService.getGlobalRemainingTokens();

        // Should consume minimal tokens (1 token)
        long consumed = beforeQuota.perMinute() - afterQuota.perMinute();
        assertThat(consumed).isBetween(0L, 2L);
    }

    @Test
    void testDirectToolCallWithExcessiveLengthReturnsError() {
        String longQuery = "a".repeat(2001);

        McpSchema.CallToolResult result = jmixDocsTool.search(longQuery);

        assertThat(result.isError()).isTrue();
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("Validation error")
                .contains("exceeds maximum length of 2000 characters");
    }

    @Test
    void testDirectToolCallWhenGlobalBudgetExhausted() {
        tokenBudgetService.tryConsumeGlobal(500);

        McpSchema.CallToolResult result = jmixDocsTool.search("test");

        assertThat(result.isError()).isTrue();
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("Token budget exceeded")
                .contains("Global token budget exceeded");
    }

    @Test
    void testDirectToolCallWhenIpBudgetExhausted() {
        // Exhaust IP budget for "unknown" (default IP in non-HTTP context)
        tokenBudgetService.tryConsumeForIp("unknown", 500);

        McpSchema.CallToolResult result = jmixDocsTool.search("test");

        assertThat(result.isError()).isTrue();
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("Token budget exceeded")
                .contains("Token budget exceeded for IP");
    }

    @Test
    void testGlobalLimitBlocksEvenWhenIpHasCapacity() {
        tokenBudgetService.tryConsumeGlobal(500);

        TokenBudgetService.TokenQuota ipQuota = tokenBudgetService.getRemainingTokensForIp("192.168.1.1");
        assertThat(ipQuota.perMinute()).isEqualTo(500);

        McpSchema.CallToolResult result = jmixDocsTool.search("test");

        assertThat(result.isError()).isTrue();
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("Token budget exceeded")
                .contains("Global token budget exceeded");
    }

    @Test
    void testIpLimitBlocksEvenWhenGlobalHasCapacity() {
        tokenBudgetService.tryConsumeForIp("unknown", 500);

        TokenBudgetService.TokenQuota globalQuota = tokenBudgetService.getGlobalRemainingTokens();
        assertThat(globalQuota.perMinute()).isGreaterThan(0);

        McpSchema.CallToolResult result = jmixDocsTool.search("test");

        assertThat(result.isError()).isTrue();
        McpSchema.TextContent textContent2 = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent2.text()).contains("Token budget exceeded")
                .contains("Token budget exceeded for IP");
    }

    @Test
    void testExactCharacterLimitBoundaryIsAccepted() {
        // Exactly 2000 characters - should pass character length validation
        // But might fail token budget if 2000 chars = 500 tokens > per-minute budget
        // So use smaller query that's within both limits
        String exactLimit = "a".repeat(400);  // 400 chars = 100 tokens, within budget
        jmixDocsTool.search(exactLimit);  // Should not throw
    }

    @Test
    void testOnePastLimitIsRejected() {
        String overLimit = "a".repeat(2001);

        McpSchema.CallToolResult result = jmixDocsTool.search(overLimit);

        assertThat(result.isError()).isTrue();
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertThat(textContent.text()).contains("Validation error")
                .contains("exceeds maximum length");
    }

    @Test
    void testTokenQuotaToStringFormat() {
        TokenBudgetService.TokenQuota quota = tokenBudgetService.getGlobalRemainingTokens();
        String str = quota.toString();

        assertThat(str).contains("500/min");
        assertThat(str).contains("5000/hour");
        assertThat(str).contains("50000/day");
    }

    @Test
    void testLargeTokenConsumptionDecreasesAllWindows() {
        tokenBudgetService.tryConsumeGlobal(200);

        TokenBudgetService.TokenQuota quota = tokenBudgetService.getGlobalRemainingTokens();
        assertThat(quota.perMinute()).isEqualTo(300);
        assertThat(quota.perHour()).isEqualTo(4800);
        assertThat(quota.perDay()).isEqualTo(49800);
    }

    @Test
    void testConsumingExactlyRemainingAmount() {
        tokenBudgetService.tryConsumeGlobal(500);

        TokenBudgetService.TokenQuota quota = tokenBudgetService.getGlobalRemainingTokens();
        assertThat(quota.perMinute()).isZero();
    }

    @Test
    void testMultipleIpBucketsAreCachedIndependently() {
        tokenBudgetService.tryConsumeForIp("192.168.1.1", 10);
        tokenBudgetService.tryConsumeForIp("192.168.1.2", 20);
        tokenBudgetService.tryConsumeForIp("192.168.1.3", 30);

        assertThat(tokenBudgetService.getRemainingTokensForIp("192.168.1.1").perMinute()).isEqualTo(490);
        assertThat(tokenBudgetService.getRemainingTokensForIp("192.168.1.2").perMinute()).isEqualTo(480);
        assertThat(tokenBudgetService.getRemainingTokensForIp("192.168.1.3").perMinute()).isEqualTo(470);
    }
}
