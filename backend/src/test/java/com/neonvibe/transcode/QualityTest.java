package com.neonvibe.transcode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Quality} preset mapping and parsing.
 */
class QualityTest {

    @Test
    void fromParam_isCaseInsensitive() {
        assertThat(Quality.fromParam("NORMAL")).isEqualTo(Quality.NORMAL);
        assertThat(Quality.fromParam(" high ")).isEqualTo(Quality.HIGH);
    }

    @Test
    void fromParam_fallsBackToOriginalForUnknownOrNull() {
        assertThat(Quality.fromParam(null)).isEqualTo(Quality.ORIGINAL);
        assertThat(Quality.fromParam("bogus")).isEqualTo(Quality.ORIGINAL);
    }

    @Test
    void targetBitrates() {
        assertThat(Quality.ORIGINAL.targetKbps()).isZero();
        assertThat(Quality.HIGH.targetKbps()).isEqualTo(320);
        assertThat(Quality.NORMAL.targetKbps()).isEqualTo(192);
        assertThat(Quality.DATA.targetKbps()).isEqualTo(128);
    }

    @Test
    void params_exposesAllPresetsInOrder() {
        assertThat(Quality.params()).containsExactly("original", "high", "normal", "data");
    }
}
