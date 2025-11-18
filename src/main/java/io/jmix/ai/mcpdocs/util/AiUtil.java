package io.jmix.ai.mcpdocs.util;

import io.jmix.ai.mcpdocs.service.Reranker;
import org.springframework.ai.document.Document;

import java.util.List;

public class AiUtil {

    public static String getUrlOrSource(Document document) {
        String url = (String) document.getMetadata().get("url");
        if (url != null)
            return url;
        else
            return (String) document.getMetadata().get("source");
    }

    public static String getDocSourcesAsString(List<Document> documents) {
        return documents.stream()
                .map(document ->
                        "(" + String.format("%.3f", document.getScore()) + ") " + getUrlOrSource(document))
                .toList()
                .toString();
    }

    public static String getRerankResultsAsString(List<Reranker.Result> rerankResults) {
        return rerankResults.stream()
                .map(rr ->
                        "(" + String.format("%.3f", rr.score()) + ") " + getUrlOrSource(rr.document()))
                .toList()
                .toString();
    }
}
