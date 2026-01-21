package com.postage.postagecomparator.provider;

import com.postage.postagecomparator.config.ProviderConfig;
import com.postage.postagecomparator.model.Item;
import com.postage.postagecomparator.model.OriginSettings;
import com.postage.postagecomparator.model.Packaging;
import com.postage.postagecomparator.model.CarrierQuote;
import com.postage.postagecomparator.model.ShipmentRequest;

import java.util.List;
import java.util.Optional;

/**
 * SPI contract for carrier providers (AusPost, Shippit, etc.).
 * Implementations should encapsulate provider-specific API logic.
 */
public interface CarrierProvider {

    String getName();

    /**
     * Determine if this provider is enabled based on configuration.
     */
    boolean isEnabled(ProviderConfig config);

    /**
     * Attempt to quote using provider APIs.
     * Returns Optional.empty() on failure to allow rules-based fallback.
     */
    Optional<CarrierQuote> quote(ShipmentRequest request,
            OriginSettings origin,
            Packaging packaging,
            List<Item> items);

    /**
     * Attempt to quote using provider APIs.
     * Returns Optional.empty() on failure to allow rules-based fallback.
     */
    Optional<List<CarrierQuote>> quotes(ShipmentRequest request,
            OriginSettings origin,
            Packaging packaging,
            List<Item> items);
}
