package io.jmix.ai.mcpdocs.validation;

import io.jmix.ai.mcpdocs.util.McpHttpHeaders;
import io.jmix.ai.mcpdocs.config.RateLimitProperties;
import io.jmix.ai.mcpdocs.service.RateLimitService;
import io.jmix.ai.mcpdocs.service.TokenBudgetService;
import io.jmix.ai.mcpdocs.service.TokenEstimator;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Unified validator for MCP requests.
 * Performs all validation checks in order:
 * 1. Rate limiting (request-based)
 * 2. Input validation (blank, length, token count)
 * 3. Token budget (global and per-IP)
 * Returns the first error encountered or empty if all validations pass.
 * Search options (jmixVersion, maxResults, tokens) are intentionally NOT
 * validated here: the backend owns that contract, and its rejection is
 * proxied to the MCP client as a readable error.
 * Designed for low coupling - can be used standalone or integrated with telemetry.
 */
@Service
public class McpRequestValidator {

    private static final Logger logger = LoggerFactory.getLogger(McpRequestValidator.class);

    private final RateLimitService rateLimitService;
    private final TokenEstimator tokenEstimator;
    private final TokenBudgetService tokenBudgetService;
    private final RateLimitProperties rateLimitProperties;

    public McpRequestValidator(RateLimitService rateLimitService,
                               TokenEstimator tokenEstimator,
                               TokenBudgetService tokenBudgetService,
                               RateLimitProperties rateLimitProperties) {
        this.rateLimitService = rateLimitService;
        this.tokenEstimator = tokenEstimator;
        this.tokenBudgetService = tokenBudgetService;
        this.rateLimitProperties = rateLimitProperties;
    }

    /**
     * Validates MCP request and consumes resources if validation passes.
     *
     * @param queryText query to validate
     * @return Optional containing McpError if validation failed, empty if passed
     */
    public Optional<McpError> validateAll(String queryText) {
        String ipAddress = getClientIp();

        // 1. Check rate limits first
        Optional<McpError> rateLimitError = checkRateLimits(ipAddress);
        if (rateLimitError.isPresent()) {
            return rateLimitError;
        }

        // 2. Validate input
        Optional<McpError> inputError = validateInput(queryText);
        if (inputError.isPresent()) {
            return inputError;
        }

        // 3. Check token budgets and consume if available
        return checkAndConsumeTokenBudget(queryText, ipAddress);
    }

    /**
     * Maps McpError to MCP-compliant error response.
     * All errors are returned with isError=true (HTTP 200).
     * Uses short messages to avoid truncation.
     */
    public McpSchema.CallToolResult toErrorResponse(McpError error) {
        // Use short messages to prevent buffer issues
        String errorMessage = switch (error.getType()) {
            case VALIDATION -> error.getMessage();
            case TOKEN_BUDGET -> error.getMessage();
            case RATE_LIMIT -> "Rate limit exceeded. Retry after " +
                (error.hasRetryAfter() ? error.getRetryAfterSeconds() + "s" : "later");
        };

        return McpSchema.CallToolResult.builder()
                .addTextContent(errorMessage)
                .isError(true)
                .build();
    }

    /**
     * Check request-based rate limits (global and per-IP).
     */
    private Optional<McpError> checkRateLimits(String ipAddress) {
        // Check global rate limit first
        if (!rateLimitService.tryConsumeGlobal()) {
            long retryAfter = rateLimitService.getGlobalRefillDurationSeconds();
            logger.warn("Global rate limit exceeded");
            return Optional.of(McpError.rateLimit("Global limit", retryAfter));
        }

        // Check per-IP rate limit
        if (!rateLimitService.tryConsumeForIp(ipAddress)) {
            long retryAfter = rateLimitService.getIpRefillDurationSeconds();
            logger.warn("IP rate limit exceeded for IP: {}", ipAddress);
            return Optional.of(McpError.rateLimit("IP limit", retryAfter));
        }

        return Optional.empty();
    }

    /**
     * Validate input query (blank check, length, estimated tokens).
     */
    private Optional<McpError> validateInput(String queryText) {
        // Check for blank queries
        if (queryText == null || queryText.isBlank()) {
            String errorMsg = "Validation error: Query text cannot be null or blank";
            logger.warn(errorMsg);
            return Optional.of(McpError.validation(errorMsg));
        }

        // Validate input length
        int maxLength = rateLimitProperties.getInput().getMaxQueryLength();
        if (queryText.length() > maxLength) {
            String errorMsg = String.format("Validation error: Query text exceeds maximum length of %d characters (got %d)",
                    maxLength, queryText.length());
            logger.warn(errorMsg);
            return Optional.of(McpError.validation(errorMsg));
        }

        // Estimate and validate token count
        int estimatedTokens = tokenEstimator.estimateInputTokens(queryText);
        int maxTokens = rateLimitProperties.getInput().getMaxEstimatedInputTokens();
        if (estimatedTokens > maxTokens) {
            String errorMsg = String.format("Validation error: Estimated input tokens (%d) exceeds maximum of %d",
                    estimatedTokens, maxTokens);
            logger.warn(errorMsg);
            return Optional.of(McpError.validation(errorMsg));
        }

        return Optional.empty();
    }

    /**
     * Check token budgets and consume if available.
     */
    private Optional<McpError> checkAndConsumeTokenBudget(String queryText, String ipAddress) {
        int estimatedTokens = tokenEstimator.estimateInputTokens(queryText);

        // Skip if no tokens
        if (estimatedTokens == 0) {
            logger.debug("Skipping token budget check for zero-token query");
            return Optional.empty();
        }

        // Check global token budget
        if (!tokenBudgetService.tryConsumeGlobal(estimatedTokens)) {
            TokenBudgetService.TokenQuota remaining = tokenBudgetService.getGlobalRemainingTokens();
            String errorMsg = String.format("Token budget exceeded: Global token budget exceeded. Remaining: %s", remaining);
            logger.warn(errorMsg);
            return Optional.of(McpError.tokenBudget(errorMsg));
        }

        // Check per-IP token budget
        if (!tokenBudgetService.tryConsumeForIp(ipAddress, estimatedTokens)) {
            TokenBudgetService.TokenQuota remaining = tokenBudgetService.getRemainingTokensForIp(ipAddress);
            String errorMsg = String.format("Token budget exceeded: Token budget exceeded for IP %s. Remaining: %s", ipAddress, remaining);
            logger.warn(errorMsg);
            return Optional.of(McpError.tokenBudget(errorMsg));
        }

        logger.debug("All validations passed. IP: {}, Tokens: {}", ipAddress, estimatedTokens);
        return Optional.empty();
    }

    /**
     * Extract client IP address from current HTTP request.
     */
    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }

        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader(McpHttpHeaders.X_FORWARDED_FOR);

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader(McpHttpHeaders.X_REAL_IP);
        }

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // Handle multiple IPs in X-Forwarded-For (take the first one)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip != null ? ip : "unknown";
    }
}
