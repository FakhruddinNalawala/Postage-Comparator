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

        @NotEmpty(message = "Please select at least one item")
        List<@Valid ShipmentItemSelection> items,

        /**
         * When true, only AusPost quotes will be returned.
         * This is set when the destination is a PO Box or Parcel Locker,
         * as other carriers cannot deliver to these addresses.
         */
        Boolean ausPostOnly
) {
    /**
     * Helper method to check if only AusPost should be used.
     */
    public boolean isAusPostOnly() {
        return ausPostOnly != null && ausPostOnly;
    }
}
