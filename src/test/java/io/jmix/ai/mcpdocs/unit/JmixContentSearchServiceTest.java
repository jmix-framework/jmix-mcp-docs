package io.jmix.ai.mcpdocs.unit;

import io.jmix.ai.mcpdocs.service.BackendRequestException;
import io.jmix.ai.mcpdocs.service.JmixContentSearchService;
import io.jmix.ai.mcpdocs.service.SearchOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for the backend /api/v2/search request contract:
 * endpoint, field names, omission of absent optional parameters and
 * translation of backend error responses into LLM-readable messages.
 */
class JmixContentSearchServiceTest {

    private static final String SEARCH_URL = "http://backend/api/v2/search";

    private MockRestServiceServer server;
    private JmixContentSearchService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://backend");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new JmixContentSearchService(builder.build());
    }

    @Test
    void postsOnlyTheQueryWhenNoOptionsAreSet() {
        server.expect(requestTo(SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"query\":\"how to add a button\"}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        String result = service.search("how to add a button", SearchOptions.NONE);

        assertThat(result).isEqualTo("[]");
        server.verify();
    }

    @Test
    void postsEveryProvidedOptionUnderItsApiFieldName() {
        server.expect(requestTo(SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(
                        "{\"query\":\"q\",\"jmix_version\":\"v2\",\"max_results\":5,\"tokens\":1000}",
                        JsonCompareMode.STRICT))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        service.search("q", new SearchOptions("v2", 5, 1000));

        server.verify();
    }

    @Test
    void omitsBlankJmixVersionSoTheBackendDefaultApplies() {
        server.expect(requestTo(SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"query\":\"q\",\"max_results\":3}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        service.search("q", new SearchOptions("  ", 3, null));

        server.verify();
    }

    @Test
    void forwardsTheProblemDetailReasonOfABadRequest() {
        server.expect(requestTo(SEARCH_URL))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"type\":\"about:blank\",\"title\":\"Bad Request\",\"status\":400,"
                                + "\"detail\":\"Unknown Jmix version: 2.8\",\"instance\":\"/api/v2/search\"}"));

        assertThatThrownBy(() -> service.search("q", new SearchOptions("2.8", null, null)))
                .isInstanceOf(BackendRequestException.class)
                .hasMessage("Jmix docs backend rejected the request (HTTP 400 Bad Request): "
                        + "Unknown Jmix version: 2.8");
    }

    @Test
    void forwardsTheClassicErrorMessageFieldOfABadRequest() {
        server.expect(requestTo(SEARCH_URL))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":400,\"error\":\"Bad Request\","
                                + "\"message\":\"Tokens must be between 1 and 100000\",\"path\":\"/api/v2/search\"}"));

        assertThatThrownBy(() -> service.search("q", new SearchOptions(null, null, 999_999)))
                .isInstanceOf(BackendRequestException.class)
                .hasMessage("Jmix docs backend rejected the request (HTTP 400 Bad Request): "
                        + "Tokens must be between 1 and 100000");
    }

    @Test
    void reportsTheBareStatusWhenTheErrorBodyCarriesNoReason() {
        server.expect(requestTo(SEARCH_URL))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"timestamp\":\"2026-08-26T11:46:38Z\",\"status\":400,"
                                + "\"error\":\"Bad Request\",\"path\":\"/api/v2/search\"}"));

        assertThatThrownBy(() -> service.search("q", SearchOptions.NONE))
                .isInstanceOf(BackendRequestException.class)
                .hasMessage("Jmix docs backend rejected the request (HTTP 400 Bad Request)");
    }

    @Test
    void neverForwardsServerErrorBodiesEvenWhenTheyCarryDetails() {
        server.expect(requestTo(SEARCH_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"status\":503,\"detail\":\"connection pool exhausted at DbPool.java:42\"}"));

        assertThatThrownBy(() -> service.search("q", SearchOptions.NONE))
                .isInstanceOf(BackendRequestException.class)
                .hasMessage("Jmix docs backend rejected the request (HTTP 503 Service Unavailable)");
    }
}
