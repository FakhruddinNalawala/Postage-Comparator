package com.postage.postagecomparator.util;

import java.util.Objects;

/**
 * Utility class for calculating delivery ETA (estimated time of arrival) based on
 * service type (express/standard), state (same/interstate), and postcode types (metro/rural).
 */
public final class DeliveryEtaUtils {

    private DeliveryEtaUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Result record containing min and max delivery days.
     */
    public record EtaResult(int minDays, int maxDays) {
    }

    /**
     * Calculates delivery ETA based on service type, state, and postcode types.
     *
     * @param originPostcode origin postcode as integer
     * @param destinationPostcode destination postcode as integer
     * @param originState origin state code (e.g., "NSW", "VIC")
     * @param destinationState destination state code (e.g., "NSW", "VIC")
     * @param isExpress true for express service, false for standard
     * @return EtaResult with minDays and maxDays
     */
    public static EtaResult calculateEta(
            int originPostcode,
            int destinationPostcode,
            String originState,
            String destinationState,
            boolean isExpress) {

        var sameState = Objects.equals(originState, destinationState);
        var originMetro = PostcodeUtils.isMetro(originPostcode);
        var destinationMetro = PostcodeUtils.isMetro(destinationPostcode);
        
        // Determine base ETA
        int baseMin;
        int baseMax;
        if (isExpress) {
            // Express: Same state 1-2 days, Interstate 1-3 days
            baseMin = 1;
            baseMax = sameState ? 2 : 3;
        } else {
            // Standard: Same state 2-4 days, Interstate 3-6 days
            baseMin = sameState ? 2 : 3;
            baseMax = sameState ? 4 : 6;
        }
        
        // Apply rural adjustments
        int maxAdjustment = 0;
        if (!originMetro && !destinationMetro) {
            // Both rural
            maxAdjustment = isExpress ? 2 : 3;
        } else if (!originMetro || !destinationMetro) {
            // Metro to rural or rural to metro
            maxAdjustment = isExpress ? 1 : 2;
        }
        // If both metro, no adjustment needed (maxAdjustment stays 0)
        
        return new EtaResult(baseMin, baseMax + maxAdjustment);
    }

    /**
     * Overloaded method that accepts postcodes as strings.
     * 
     * @param originPostcode origin postcode as string (e.g., "2000")
     * @param destinationPostcode destination postcode as string (e.g., "3000")
     * @param originState origin state code
     * @param destinationState destination state code
     * @param isExpress true for express service, false for standard
     * @return EtaResult with minDays and maxDays
     * @throws NumberFormatException if postcode strings cannot be parsed as integers
     */
    public static EtaResult calculateEta(
            String originPostcode,
            String destinationPostcode,
            String originState,
            String destinationState,
            boolean isExpress) {
        
        var originPostcodeInt = Integer.parseInt(originPostcode);
        var destinationPostcodeInt = Integer.parseInt(destinationPostcode);
        
        return calculateEta(originPostcodeInt, destinationPostcodeInt, originState, destinationState, isExpress);
    }
}
