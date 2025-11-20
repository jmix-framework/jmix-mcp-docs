package io.jmix.ai.mcpdocs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Value("${jmix.ai.backend.url}")
    private String backendUrl;

    @Bean
    public RestClient jmixAiRestClient() {
        return RestClient.builder()
                .baseUrl(backendUrl)
                .build();
    }
}