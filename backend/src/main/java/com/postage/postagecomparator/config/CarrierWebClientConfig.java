package com.postage.postagecomparator.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Central configuration for carrier API WebClients.
 *
 * These clients are preconfigured with the appropriate base URLs so they can be
 * injected wherever carrier API calls are needed.
 *
 * Authentication headers (API keys) and any per-request headers should be added
 * by the calling services, or this config can be extended later to apply them
 * globally.
 */
@Configuration
public class CarrierWebClientConfig {

    /**
     * WebClient for Australia Post's digital API.
     * Base URL: https://digitalapi.auspost.com.au
     */
    @Bean
    @Qualifier("ausPostWebClient")
    public WebClient ausPostWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl("https://digitalapi.auspost.com.au")
                .build();
    }

    /*
     * Sendle integration is currently disabled.
     *
     * WebClient for Sendle's sandbox environment.
     * Base URL: https://sandbox.sendle.com
     */
    /*
    @Bean
    @Qualifier("sendleWebClient")
    public WebClient sendleWebClient(WebClient.Builder builder) {
        var sendleMode = System.getenv("SENDLE_MODE");
        String url = (sendleMode != null && sendleMode.equalsIgnoreCase("sandbox"))
                ? "https://sandbox.sendle.com"
                : "https://api.sendle.com";
        return builder
                .baseUrl(url)
                .build();
    }
    */
}
