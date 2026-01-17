package com.postage.postagecomparator.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record OriginSettings(
        @NotBlank
        @Pattern(regexp = "\\d{4}", message = "Postcode must be 4 digits")
        String postcode,
        @NotBlank
        String suburb,
        @NotBlank
        String state,
        @NotBlank
        String country,
        @Pattern(regexp = "(?i)dark|light|sepia", message = "Theme preference must be dark, light, or sepia")
        String themePreference,
        Instant updatedAt
) {
}
