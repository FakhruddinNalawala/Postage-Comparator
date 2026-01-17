package com.postage.postagecomparator.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record Packaging(
        String id,

        @NotBlank
        String name,

        String description,

        @Positive
        int lengthCm,

        @Positive
        int heightCm,

        @Positive
        int widthCm,
        
        int internalVolumeCubicCm,

        @Positive
        double packagingCostAud
) {
}
