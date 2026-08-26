package io.jmix.ai.mcpdocs.util.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock search result builder with Jackson serialization.
 * Mirrors the backend /api/v2/search response item shape: id, title, source, content.
 */
public class MockSearchResult {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<SearchItem> items = new ArrayList<>();

    public static MockSearchResult create() {
        return new MockSearchResult();
    }

    public MockSearchResult addItem(String title, String source, String content) {
        items.add(new SearchItem("doc-" + (items.size() + 1), title, source, content));
        return this;
    }

    public String asJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize mock response", e);
        }
    }

    public record SearchItem(String id, String title, String source, String content) {
    }
}
