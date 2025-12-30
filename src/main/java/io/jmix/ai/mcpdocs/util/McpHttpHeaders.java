package io.jmix.ai.mcpdocs.util;

/**
 * HTTP header constants used throughout the MCP application.
 * Centralizes all header names to ensure consistency across
 * application code and tests.
 */
public final class McpHttpHeaders {

    private McpHttpHeaders() {
        // Utility class - prevent instantiation
    }

    // Client IP detection headers
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    public static final String X_REAL_IP = "X-Real-IP";
}
