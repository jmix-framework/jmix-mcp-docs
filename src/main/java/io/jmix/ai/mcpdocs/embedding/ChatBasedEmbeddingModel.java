package io.jmix.ai.mcpdocs.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ChatBasedEmbeddingModel(AnthropicChatModel chatModel) implements EmbeddingModel {

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();

        List<String> instructions = request.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
            String text = instructions.get(i);
            String embeddingPrompt = createEmbeddingPrompt(text);
            ChatResponse response = chatModel.call(new Prompt(embeddingPrompt));
            String embeddingText = response.getResult().getOutput().getText();

            assert embeddingText != null;
            float[] embedding = parseEmbeddingFromResponse(embeddingText);
            embeddings.add(new Embedding(embedding, i));
        }

        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        String text = document.getText();
        EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
        EmbeddingResponse response = this.call(request);
        if (response.getResults().isEmpty()) {
            return new float[0];
        }
        return response.getResults().getFirst().getOutput();
    }

    private String createEmbeddingPrompt(String text) {
        return """
                You are an embedding function.
                Output ONLY a JSON array of floats (no prose, no keys).
                Length MUST be 1536. Values in [-1,1].
                Text:
                """ + text;
    }


    private static final Pattern FLOAT_RE = Pattern.compile(
            "[-+]?(?:\\d*\\.\\d+|\\d+)(?:[eE][-+]?\\d+)?"
    );

    private float[] parseEmbeddingFromResponse(String response) {
        // 1) сначала попробуем распарсить как JSON-массив
        try {
            var arr = new ObjectMapper().readValue(response, float[].class);
            return normalizeToDim(arr, 1536);
        } catch (Exception ignore) {
            // 2) fallback: вытянуть все числа регэкспом
            Matcher m = FLOAT_RE.matcher(response);
            List<Float> vals = new ArrayList<>();
            while (m.find()) vals.add(Float.parseFloat(m.group()));
            if (vals.isEmpty()) {
                throw new IllegalStateException("No floats in model response: " + response);
            }
            float[] out = new float[vals.size()];
            for (int i = 0; i < vals.size(); i++) out[i] = vals.get(i);
            return normalizeToDim(out, 1536);
        }
    }

    private float[] normalizeToDim(float[] v, int dim) {
        if (v.length == dim) return v;
        float[] out = new float[dim];
        // если меньше — паддинг нулями; если больше — усечение
        System.arraycopy(v, 0, out, 0, Math.min(v.length, dim));
        return out;
    }

}
