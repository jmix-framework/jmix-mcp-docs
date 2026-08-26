package io.jmix.ai.mcpdocs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JmixContentSearchService {

    private static final ObjectMapper ERROR_BODY_MAPPER = new ObjectMapper();

    private final RestClient restClient;

    public JmixContentSearchService(@Qualifier("jmixAiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public String search(String query, SearchOptions options) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("query", query);
        if (options.jmixVersion() != null && !options.jmixVersion().isBlank()) {
            requestBody.put("jmix_version", options.jmixVersion());
        }
        if (options.maxResults() != null) {
            requestBody.put("max_results", options.maxResults());
        }
        if (options.tokens() != null) {
            requestBody.put("tokens", options.tokens());
        }

        try {
            return restClient.post()
                    .uri("/api/v2/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new BackendRequestException(describeBackendError(e), e);
        }
    }

    private static String describeBackendError(RestClientResponseException e) {
        StringBuilder message = new StringBuilder("Jmix docs backend rejected the request (HTTP ")
                .append(e.getStatusCode().value());
        // resolved locally: servers may omit the reason phrase from the raw response
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status != null) {
            message.append(' ').append(status.getReasonPhrase());
        }
        message.append(')');

        // the reason is forwarded for client errors only: 4xx reasons are intentional
        // contract texts, while 5xx bodies may carry internals not meant for MCP clients
        String reason = e.getStatusCode().is4xxClientError()
                ? extractReason(e.getResponseBodyAsString())
                : null;
        if (reason != null) {
            message.append(": ").append(reason);
        }
        return message.toString();
    }

    /**
     * Pulls the rejection reason out of the backend error body: {@code detail}
     * of a ProblemDetail response or {@code message} of the classic Spring Boot
     * error JSON. Returns null when the body carries neither.
     */
    private static String extractReason(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode json = ERROR_BODY_MAPPER.readTree(body);
            for (String field : new String[]{"detail", "message"}) {
                JsonNode value = json.get(field);
                if (value != null && value.isTextual() && !value.asText().isBlank()) {
                    return value.asText();
                }
            }
        } catch (Exception notJson) {
            // fall through to the status-line/hint fallback
        }
        return null;
    }
}
