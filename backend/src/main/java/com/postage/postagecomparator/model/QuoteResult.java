package com.postage.postagecomparator.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.List;

public record QuoteResult(
        @PositiveOrZero
        int totalWeightGrams,

        @PositiveOrZero
        double weightInKg,

        @PositiveOrZero
        double volumeWeightInKg,

        @PositiveOrZero
        int totalVolumeCubicCm,

        @NotNull
        @Valid
        OriginSettings origin,

        @NotNull
        @Valid
        Destination destination,

        @NotNull
        @Valid
        Packaging packaging,

        @NotNull
        List<@Valid CarrierQuote> carrierQuotes,

        @NotBlank
        String currency,

        @NotNull
        Instant generatedAt
) {
    // Destination is a simple nested record to mirror the JSON shape
    public record Destination(
            @NotBlank
            String postcode,

            @NotBlank
            String suburb,

            @NotBlank
            String state,

            @NotBlank
            String country
    ) {
    }
}
