package io.jmix.ai.mcpdocs.service;

/**
 * Signals that the backend answered the search request with an error status.
 * The message is written for the MCP client's LLM: status line plus the
 * backend-provided reason.
 */
public class BackendRequestException extends RuntimeException {

    public BackendRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
