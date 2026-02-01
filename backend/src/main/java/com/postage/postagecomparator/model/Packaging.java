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

        @Positive
        double packagingCostAud
) {
    /**
     * Calculates the internal volume from the dimensions.
     * @return the volume in cubic centimeters
     */
    public int volumeCubicCm() {
        return lengthCm * heightCm * widthCm;
    }
}
