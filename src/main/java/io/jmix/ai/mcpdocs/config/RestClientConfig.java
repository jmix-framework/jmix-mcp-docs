package io.jmix.ai.mcpdocs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Value("${jmix.ai.backend.url}")
    private String backendUrl;

    @Value("${jmix.ai.backend.connect-timeout:10s}")
    private Duration connectTimeout;

    @Value("${jmix.ai.backend.read-timeout:60s}")
    private Duration readTimeout;

    @Bean
    public RestClient jmixAiRestClient() {
        return RestClient.builder()
                .baseUrl(backendUrl)
                .requestFactory(clientHttpRequestFactory())
                .build();
    }

    private ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}