package com.fleet.analytics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Exposes a shared {@link RestClient} used to pull data from downstream services
 * (e.g. the diagnostics service). We use the modern {@code RestClient} rather than
 * the deprecated {@code RestTemplate} or the reactive {@code WebClient}.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
