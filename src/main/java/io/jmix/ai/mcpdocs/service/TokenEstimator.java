package io.jmix.ai.mcpdocs.service;

import org.springframework.stereotype.Service;

/**
 * Estimates token count for text input.
 * Based on OpenAI's guidelines:
 * - 1 token ≈ 4 characters in English
 * - 1 token ≈ ¾ words
 */
@Service
public class TokenEstimator {

    private static final double CHARS_PER_TOKEN = 4.0;

    public int estimateInputTokens(String queryText) {
        if (queryText == null || queryText.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(queryText.length() / CHARS_PER_TOKEN);
    }
}
