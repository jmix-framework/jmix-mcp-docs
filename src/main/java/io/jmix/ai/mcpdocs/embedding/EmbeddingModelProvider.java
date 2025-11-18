package io.jmix.ai.mcpdocs.embedding;

import org.springframework.ai.embedding.EmbeddingModel;

public interface EmbeddingModelProvider {
    String getProviderName();
    EmbeddingModel getEmbeddingModel();
    int getDimensions();
}
