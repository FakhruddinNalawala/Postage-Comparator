package com.postage.postagecomparator.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostcodeUtilsTest {

    @Test
    void isMetro_returnsTrue_forPostcodeInsideRange() {
        // 3000 is inside [3000, 3062]
        assertThat(PostcodeUtils.isMetro(3000)).isTrue();
    }

    @Test
    void isMetro_returnsTrue_forBoundaryValues() {
        // Lower and upper bounds of a known range
        assertThat(PostcodeUtils.isMetro(3000)).isTrue();
        assertThat(PostcodeUtils.isMetro(3062)).isTrue();
    }

    @Test
    void isMetro_returnsFalse_forPostcodeOutsideAllRanges() {
        // 3999 is outside any configured metro range
        assertThat(PostcodeUtils.isMetro(3999)).isFalse();
        // 50 is clearly outside all configured ranges
        assertThat(PostcodeUtils.isMetro(50)).isFalse();
    }

    @Test
    void isMetro_returnsTrue_forHighSpecialRanges() {
        // 8000 and 8999 fall in [8000, 8999]
        assertThat(PostcodeUtils.isMetro(8000)).isTrue();
        assertThat(PostcodeUtils.isMetro(8999)).isTrue();
    }
}

