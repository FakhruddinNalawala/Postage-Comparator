package com.postage.postagecomparator.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryEtaUtilsTest {

    @Test
    void calculateEta_express_sameState_bothMetro() {
        // 3000 and 3004 are metro VIC according to PostcodeUtils ranges
        var eta = DeliveryEtaUtils.calculateEta(3000, 3004, "VIC", "VIC", true);

        assertThat(eta.minDays()).isEqualTo(1);
        assertThat(eta.maxDays()).isEqualTo(2);
    }

    @Test
    void calculateEta_express_interstate_bothMetro() {
        // 3000 VIC (metro) to 4000 QLD (metro)
        var eta = DeliveryEtaUtils.calculateEta(3000, 4000, "VIC", "QLD", true);

        assertThat(eta.minDays()).isEqualTo(1);
        assertThat(eta.maxDays()).isEqualTo(3);
    }

    @Test
    void calculateEta_express_sameState_metroToRural_addsOneDay() {
        // 3000 VIC (metro) to 3999 VIC (assumed rural, outside metro ranges)
        var eta = DeliveryEtaUtils.calculateEta(3000, 3999, "VIC", "VIC", true);

        // base 1–2 days for express same state, +1 for metro↔rural
        assertThat(eta.minDays()).isEqualTo(1);
        assertThat(eta.maxDays()).isEqualTo(3);
    }

    @Test
    void calculateEta_standard_interstate_bothRural_addsThreeDays() {
        // 7000 TAS and 7999 TAS – assumed rural (not in metro list)
        var eta = DeliveryEtaUtils.calculateEta(7000, 7999, "TAS", "NSW", false);

        // base 3–6 days for standard interstate, +3 for rural↔rural
        assertThat(eta.minDays()).isEqualTo(3);
        assertThat(eta.maxDays()).isEqualTo(9);
    }

    @Test
    void calculateEta_stringOverload_matchesIntVersion() {
        var fromInts = DeliveryEtaUtils.calculateEta(3000, 4000, "VIC", "QLD", true);
        var fromStrings = DeliveryEtaUtils.calculateEta("3000", "4000", "VIC", "QLD", true);

        assertThat(fromStrings.minDays()).isEqualTo(fromInts.minDays());
        assertThat(fromStrings.maxDays()).isEqualTo(fromInts.maxDays());
    }

    @Test
    void calculateEta_stringOverload_invalidPostcode_throwsNumberFormatException() {
        assertThatThrownBy(() ->
                DeliveryEtaUtils.calculateEta("ABC", "3000", "VIC", "VIC", true)
        ).isInstanceOf(NumberFormatException.class);
    }
}

