package com.postage.postagecomparator.util;

import java.util.List;

/**
 * Utility class for postcode-related operations.
 */
public final class PostcodeUtils {

    private PostcodeUtils() {
        // Utility class - prevent instantiation
    }

    private static final List<int[]> METRO_POSTCODE_RANGES = List.of(
            new int[] { 1000, 1935 },
            new int[] { 2000, 2079 },
            new int[] { 2085, 2107 },
            new int[] { 2109, 2156 },
            new int[] { 2158, 2172 },
            new int[] { 2174, 2229 },
            new int[] { 2232, 2249 },
            new int[] { 2557, 2559 },
            new int[] { 2564, 2567 },
            new int[] { 2740, 2744 },
            new int[] { 2747, 2751 },
            new int[] { 2759, 2764 },
            new int[] { 2766, 2774 },
            new int[] { 2776, 2777 },
            new int[] { 2890, 2897 },
            new int[] { 3000, 3062 },
            new int[] { 3064, 3098 },
            new int[] { 3101, 3138 },
            new int[] { 3140, 3210 },
            new int[] { 3800, 3801 },
            new int[] { 4000, 4018 },
            new int[] { 4029, 4068 },
            new int[] { 4072, 4123 },
            new int[] { 4127, 4129 },
            new int[] { 4131, 4132 },
            new int[] { 4151, 4164 },
            new int[] { 4169, 4182 },
            new int[] { 4205, 4206 },
            new int[] { 5000, 5113 },
            new int[] { 5115, 5117 },
            new int[] { 5125, 5130 },
            new int[] { 5158, 5169 },
            new int[] { 5800, 5999 },
            new int[] { 8000, 8999 },
            new int[] { 9000, 9275 },
            new int[] { 9999, 9999 });

    /**
     * Determines if a given postcode is within a metropolitan area.
     *
     * @param postcode the postcode to check (as integer)
     * @return true if the postcode falls within any of the defined metro ranges, false otherwise
     */
    public static boolean isMetro(int postcode) {
        return METRO_POSTCODE_RANGES.stream()
                .anyMatch(range -> postcode >= range[0] && postcode <= range[1]);
    }
}