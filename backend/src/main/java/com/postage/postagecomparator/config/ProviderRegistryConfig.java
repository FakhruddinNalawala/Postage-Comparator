package com.postage.postagecomparator.config;

import com.postage.postagecomparator.provider.CarrierProvider;
import com.postage.postagecomparator.provider.ProviderRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires provider SPI configuration and registry into Spring.
 */
@Configuration
@EnableConfigurationProperties(ProviderConfig.class)
public class ProviderRegistryConfig {

    /**
     * Registry of all CarrierProvider beans.
     */
    @Bean
    public ProviderRegistry providerRegistry(List<CarrierProvider> providers) {
        return new ProviderRegistry(providers);
    }
}
