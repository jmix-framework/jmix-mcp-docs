package io.jmix.ai.mcpdocs.util.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock search result builder with Jackson serialization.
 */
public class MockSearchResult {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<SearchItem> items = new ArrayList<>();

    public static MockSearchResult create() {
        return new MockSearchResult();
    }

    public MockSearchResult addItem(String title, String content, String url, double score) {
        items.add(new SearchItem(title, content, url, score));
        return this;
    }

    public String asJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize mock response", e);
        }
    }

    public record SearchItem(String title, String content, String url, double score) {
    }
}
