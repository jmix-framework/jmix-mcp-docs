package io.jmix.ai.mcpdocs.embedding;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.embedding.model.type", havingValue = "openai")
public class OpenAiEmbeddingModelProvider implements EmbeddingModelProvider {

    @Value("${app.embedding.openai.api-key:${OPENAI_API_KEY}}")
    private String apiKey;

    @Value("${app.embedding.openai.model:text-embedding-3-small}")
    private String modelName;

    @Value("${app.embedding.openai.base-url:https://api.openai.com}")
    private String baseUrl;

    @Override
    public String getProviderName() {
        return "OpenAI: " + modelName;
    }

    @Override
    public EmbeddingModel getEmbeddingModel() {
        if (StringUtils.isBlank(apiKey)) {
            throw new IllegalStateException("OpenAI API key is required for OpenAI embeddings");
        }

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, OpenAiEmbeddingOptions.builder()
                .model(modelName)
                .dimensions(getDimensions())
                .build());
    }

    @Override
    public int getDimensions() {
        return switch (modelName) {
            case "text-embedding-3-large" -> 3072;
            case "text-embedding-3-small", "text-embedding-ada-002" -> 1536;
            default -> EmbeddingModelType.OPENAI.getDimensions();
        };
    }
}
