package com.postage.postagecomparator.model;

import jakarta.validation.constraints.Pattern;

public record ThemePreferenceRequest(
        @Pattern(regexp = "(?i)dark|light|sepia", message = "Theme preference must be dark, light, or sepia")
        String themePreference
) {
}
