package io.jmix.ai.mcpdocs.embedding;

public enum EmbeddingModelType {
    OPENAI("openai", 1536),
    CLAUDE("claude", 1024);

    private final String configValue;
    private final int dimensions;

    EmbeddingModelType(String configValue, int dimensions) {
        this.configValue = configValue;
        this.dimensions = dimensions;
    }

    public String getConfigValue() { return configValue; }
    public int getDimensions() { return dimensions; }
}
