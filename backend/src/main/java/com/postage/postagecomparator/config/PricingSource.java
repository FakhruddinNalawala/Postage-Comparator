package com.postage.postagecomparator.config;

import java.util.Arrays;
import java.util.Optional;

public enum PricingSource {
    AUSPOST_API("AUSPOST_API", "AusPost API"),
    SHIPPIT_API("SHIPPIT_API", "Shippit API"),
    SHIPSTATION_API("SHIPSTATION_API", "ShipStation API");

    private final String code;
    private final String label;

    PricingSource(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static Optional<PricingSource> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(source -> source.code.equalsIgnoreCase(code))
                .findFirst();
    }
}
