package com.postage.postagecomparator.provider;

import com.postage.postagecomparator.config.ProviderConfig;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry for discovering and selecting carrier providers.
 * Intended to be constructed via DI with all available providers.
 */
public class ProviderRegistry {

    private final Map<String, CarrierProvider> providersByName;

    public ProviderRegistry(List<CarrierProvider> providers) {
        this.providersByName = providers.stream()
                .collect(Collectors.toUnmodifiableMap(CarrierProvider::getName, Function.identity()));
    }

    public List<CarrierProvider> getAllProviders() {
        return Collections.unmodifiableList(providersByName.values().stream().toList());
    }

    public List<CarrierProvider> getEnabledProviders(ProviderConfig config) {
        if (config == null || config.getProviders().isEmpty()) {
            return getAllProviders();
        }
        return providersByName.values().stream()
                .filter(provider -> provider.isEnabled(config))
                .toList();
    }

    public CarrierProvider getProvider(String name) {
        return providersByName.get(name);
    }
}
