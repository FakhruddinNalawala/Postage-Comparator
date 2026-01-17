package com.postage.postagecomparator.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record ShipmentRequest(
        @NotBlank
        @Pattern(regexp = "\\d{4}", message = "Destination postcode must be 4 digits")
        String destinationPostcode,

        String destinationSuburb,

        String destinationState,

        @NotBlank
        @Pattern(regexp = "[A-Za-z]{2}", message = "Country must be 2 letters")
        String country,

        @NotEmpty
        List<@Valid ShipmentItemSelection> items,

        @NotBlank
        String packagingId,

        boolean isExpress
) {
}
