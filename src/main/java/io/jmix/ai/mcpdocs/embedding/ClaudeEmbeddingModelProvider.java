package io.jmix.ai.mcpdocs.embedding;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.embedding.model.type", havingValue = "claude")
public class ClaudeEmbeddingModelProvider implements EmbeddingModelProvider {

    @Value("${app.embedding.claude.api-key:${ANTHROPIC_API_KEY}}")
    private String apiKey;

    @Value("${app.embedding.claude.model:claude-3-haiku-20240307}")
    private String modelName;

    @Value("${app.embedding.claude.base-url:https://api.anthropic.com}")
    private String baseUrl;

    @Override
    public String getProviderName() {
        return "Claude: " + modelName;
    }

    @Override
    public EmbeddingModel getEmbeddingModel() {
        AnthropicApi anthropicApi = AnthropicApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        return createEmbeddingModelViaChat(anthropicApi);

    }

    @Override
    public int getDimensions() {
        return modelName.contains("haiku") ? 1024 : 2048;
    }

    private EmbeddingModel createEmbeddingModelViaChat(AnthropicApi anthropicApi) {
        AnthropicChatModel chatModel = AnthropicChatModel.builder()
                .anthropicApi(anthropicApi)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(modelName)
                        .temperature(0.0)
                        .maxTokens(256 * 16)
                        .build())
                .build();

        return new ChatBasedEmbeddingModel(chatModel);
    }
}




