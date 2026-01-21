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

    /**
     * WebClient for ShipStation API.
     * Base URL defaults to production. Set APP_MODE=sandbox to use
     * https://docs.shipstation.com/_mock/openapi for tests.
     */
    @Bean
    @Qualifier("shipStationWebClient")
    public WebClient shipStationWebClient(WebClient.Builder builder) {
        var appMode = System.getenv("APP_MODE");
        String url = (appMode != null && appMode.equalsIgnoreCase("sandbox"))
                ? "https://docs.shipstation.com/_mock/openapi"
                : "https://api.shipstation.com";
        return builder
                .baseUrl(url)
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

    /*
     * WebClient for Shippit's sandbox environment.
     * Base URL: https://app.staging.shippit.com/api/3
     */
    
    @Bean
    @Qualifier("shippitWebClient")
    public WebClient shippitWebClient(WebClient.Builder builder) {
        var appMode = System.getenv("APP_MODE");
        String url = (appMode != null && appMode.equalsIgnoreCase("staging"))
                ? "https://app.staging.shippit.com/api/3"
                : "https://app.shippit.com/api/3";
        return builder
                .baseUrl(url)
                .build();
    }

    /**
     * WebClient for Aramex Rate Calculator SOAP API.
     * Base URL: http://ws.aramex.net/shippingapi/ratecalculator/service_1_0.svc
     */
    @Bean
    @Qualifier("aramexWebClient")
    public WebClient aramexWebClient(WebClient.Builder builder) {
        String baseUrl = System.getenv("ARAMEX_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://ws.aramex.net/shippingapi/ratecalculator/service_1_0.svc";
        }
        return builder
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * WebClient for AfterShip Shipping API.
     * Base URL defaults to AfterShip Shipping v3 API.
     */
    @Bean
    @Qualifier("afterShipWebClient")
    public WebClient afterShipWebClient(WebClient.Builder builder) {
        String baseUrl = System.getenv("AFTERSHIP_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.aftership.com/shipping/2024-01";
        }
        return builder
                .baseUrl(baseUrl)
                .build();
    }
    
}
