package com.postage.postagecomparator.model;

public record WeightBracket(
    double minWeightInclusive,
    double maxWeightInclusive,
    double priceStandard,
    double priceExpress
) {}
