package com.postage.postagecomparator.service;

import com.postage.postagecomparator.model.Item;
import com.postage.postagecomparator.model.OriginSettings;
import com.postage.postagecomparator.model.Packaging;
import com.postage.postagecomparator.model.QuoteResult;
import com.postage.postagecomparator.model.ShipmentItemSelection;
import com.postage.postagecomparator.model.ShipmentRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Shared request/lookup helpers for quote flow.
 * Keeps QuoteServiceImpl focused on orchestration.
 */
@Component
public class QuoteRequestHelper {

    private final SettingsService settingsService;
    private final ItemService itemService;
    private final PackagingService packagingService;

    public QuoteRequestHelper(SettingsService settingsService,
                              ItemService itemService,
                              PackagingService packagingService) {
        this.settingsService = settingsService;
        this.itemService = itemService;
        this.packagingService = packagingService;
    }

    public void validateRequest(ShipmentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ShipmentRequest must not be null");
        }
        if (request.destinationPostcode() == null || request.destinationPostcode().isBlank()) {
            throw new IllegalArgumentException("Destination postcode is required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("At least one item is required");
        }
        if (request.packagingId() == null || request.packagingId().isBlank()) {
            throw new IllegalArgumentException("Packaging is required");
        }
        for (ShipmentItemSelection itemSelection : request.items()) {
            if (itemSelection.quantity() <= 0) {
                throw new IllegalArgumentException("Item quantity must be greater than 0");
            }
        }
    }

    public OriginSettings getOriginSettingsOrThrow() {
        OriginSettings origin = settingsService.getOriginSettings();
        if (origin == null) {
            throw new IllegalStateException("Origin settings must be configured before calculating quotes");
        }
        return origin;
    }

    public Packaging getPackaging(String packagingId) {
        return packagingService.findById(packagingId)
                .orElseThrow(() -> new IllegalArgumentException("Packaging with id " + packagingId + " not found"));
    }

    public int calculateTotalWeight(List<ShipmentItemSelection> itemSelections) {
        return itemSelections.stream()
                .mapToInt(selection -> {
                    Item item = itemService.findById(selection.itemId())
                            .orElseThrow(() -> new IllegalArgumentException("Item with id " + selection.itemId() + " not found"));
                    return item.unitWeightGrams() * selection.quantity();
                })
                .sum();
    }

    /**
     * Resolve full Item records for the given selections.
     * Useful for provider-specific request mapping.
     */
    public List<Item> resolveItems(List<ShipmentItemSelection> itemSelections) {
        return itemSelections.stream()
                .map(selection -> itemService.findById(selection.itemId())
                        .orElseThrow(() -> new IllegalArgumentException("Item with id " + selection.itemId() + " not found")))
                .toList();
    }

    public QuoteResult.Destination buildDestination(ShipmentRequest request) {
        return new QuoteResult.Destination(
                request.destinationPostcode(),
                request.destinationSuburb(),
                request.destinationState(),
                Optional.ofNullable(request.country()).orElse("AU"));
    }
}
