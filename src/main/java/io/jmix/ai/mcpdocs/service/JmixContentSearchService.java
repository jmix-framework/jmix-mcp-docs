package io.jmix.ai.mcpdocs.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class JmixContentSearchService {

    private final RestClient restClient;

    public JmixContentSearchService(@Qualifier("jmixAiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public String search(String query, String type) {
        Map<String, String> requestBody =
                Map.of(
                        "query", query,
                        "type", type);

        return restClient.post()
                .uri("/api/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);
    }

    public String searchForJmixDocs(String query) {
        return search(query, "docs");
    }
}