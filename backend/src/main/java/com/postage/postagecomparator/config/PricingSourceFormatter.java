package com.postage.postagecomparator.config;

public final class PricingSourceFormatter {
    private PricingSourceFormatter() {
        // Utility class
    }

    public static String format(String source) {
        if (source == null || source.isBlank()) {
            return source;
        }
        return PricingSource.fromCode(source)
                .map(PricingSource::label)
                .orElse(source);
    }
}
