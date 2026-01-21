package com.postage.postagecomparator.config;

import com.postage.postagecomparator.provider.CarrierProvider;
import com.postage.postagecomparator.provider.ProviderRegistry;
import com.postage.postagecomparator.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Logs provider enablement and API key presence at startup to aid debugging.
 */
@Component
public class ProviderDiagnosticsLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProviderDiagnosticsLogger.class);

    private final ProviderRegistry providerRegistry;
    private final ProviderConfig providerConfig;
    private final SettingsService settingsService;

    public ProviderDiagnosticsLogger(ProviderRegistry providerRegistry,
                                     ProviderConfig providerConfig,
                                     SettingsService settingsService) {
        this.providerRegistry = providerRegistry;
        this.providerConfig = providerConfig;
        this.settingsService = settingsService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, ProviderConfig.ProviderSettings> config = providerConfig.getProviders();
        if (config == null || config.isEmpty()) {
            log.info("Provider config not set; defaulting to all providers enabled.");
        }

        List<CarrierProvider> providers = providerRegistry.getAllProviders();
        for (CarrierProvider provider : providers) {
            String name = provider.getName();
            boolean enabled = provider.isEnabled(providerConfig);
            boolean apiKeyPresent = isApiKeyPresent(name);
            log.info("Provider '{}' enabled={} apiKeyPresent={}", name, enabled, apiKeyPresent);
        }
    }

    private boolean isApiKeyPresent(String providerName) {
        return switch (providerName.toLowerCase()) {
            case "auspost" -> hasValue(settingsService.getAusPostApiKey());
            case "shippit" -> hasValue(settingsService.getShippitApiKey());
            case "shipstation" -> hasValue(settingsService.getShipStationApiKey());
            case "aftership" -> hasValue(settingsService.getAfterShipApiKey());
            default -> false;
        };
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }
}
