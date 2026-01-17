package com.postage.postagecomparator.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record Item(
        String id,

        @NotBlank
        String name,

        String description,

        @Positive
        int unitWeightGrams
) {
}
