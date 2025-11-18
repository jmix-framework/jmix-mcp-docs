package io.jmix.ai.mcpdocs.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.embedding.model.type", havingValue = "claude")
class ClaudeEmbeddingBeanConfig {

    private final ClaudeEmbeddingModelProvider provider;

    ClaudeEmbeddingBeanConfig(ClaudeEmbeddingModelProvider provider) {
        this.provider = provider;
    }

    @Bean
    EmbeddingModel embeddingModel() {
        return provider.getEmbeddingModel();
    }
}
