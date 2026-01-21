package com.postage.postagecomparator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.Optional;

/**
 * Provider configuration mapped from application config or environment variables.
 *
 * Example (application.yml):
 * providers:
 *   auspost:
 *     enabled: true
 *     apiKey: ${AUSPOST_API_KEY}
 *     apiId: ${AUSPOST_API_ID}
 */
@ConfigurationProperties(prefix = "providers")
public class ProviderConfig {

    /**
     * Keyed by provider name (e.g., "auspost", "shippit").
     */
    private Map<String, ProviderSettings> providers = Map.of();

    public Optional<ProviderSettings> getProvider(String name) {
        return Optional.ofNullable(providers.get(name));
    }

    public Map<String, ProviderSettings> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderSettings> providers) {
        this.providers = providers == null ? Map.of() : Map.copyOf(providers);
    }

    public record ProviderSettings(String apiKey, String apiId, boolean enabled) {}
}
