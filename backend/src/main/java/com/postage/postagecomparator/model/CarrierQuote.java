package com.postage.postagecomparator.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CarrierQuote(
        @NotBlank
        String carrier,

        @NotBlank
        String serviceName,

        @PositiveOrZero
        Integer deliveryEtaDaysMin,

        @PositiveOrZero
        Integer deliveryEtaDaysMax,

        @PositiveOrZero
        double packagingCostAud,

        @PositiveOrZero
        double deliveryCostAud,

        @PositiveOrZero
        Double surchargesAud,

        @PositiveOrZero
        double totalCostAud,

        @NotBlank
        String pricingSource,

        @NotNull
        boolean ruleFallbackUsed,

        String rawCarrierRef
) {
}
