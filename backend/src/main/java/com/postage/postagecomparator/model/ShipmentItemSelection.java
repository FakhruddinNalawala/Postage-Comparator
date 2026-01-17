package com.postage.postagecomparator.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ShipmentItemSelection(
        @NotBlank
        String itemId,

        @Positive
        int quantity
) {
}
