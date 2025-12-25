package io.jmix.ai.mcpdocs.validation;

/**
 * Represents a validation or operational error in MCP request processing.
 * Used to collect errors instead of throwing exceptions, allowing for
 * graceful error handling and MCP-compliant error responses.
 */
public class McpError {

    private final McpErrorType type;
    private final String message;
    private final Long retryAfterSeconds;

    private McpError(McpErrorType type, String message, Long retryAfterSeconds) {
        this.type = type;
        this.message = message;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static McpError validation(String message) {
        return new McpError(McpErrorType.VALIDATION, message, null);
    }

    public static McpError tokenBudget(String message) {
        return new McpError(McpErrorType.TOKEN_BUDGET, message, null);
    }

    public static McpError rateLimit(String message, long retryAfterSeconds) {
        return new McpError(McpErrorType.RATE_LIMIT, message, retryAfterSeconds);
    }

    public McpErrorType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public boolean hasRetryAfter() {
        return retryAfterSeconds != null;
    }

    public enum McpErrorType {
        VALIDATION,      // Input validation errors (blank, too long, too many tokens)
        TOKEN_BUDGET,    // Token budget exhausted
        RATE_LIMIT       // Request rate limit exceeded
    }
}
