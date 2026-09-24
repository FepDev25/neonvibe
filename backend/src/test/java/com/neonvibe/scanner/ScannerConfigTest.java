package com.neonvibe.scanner;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ScannerConfig} format detection and defaults.
 */
class ScannerConfigTest {

    private ScannerConfig config() {
        ScannerConfig config = new ScannerConfig();
        config.setSupportedFormats(List.of("mp3", "flac", "wav"));
        return config;
    }

    @Test
    void isFormatSupported_isCaseInsensitiveAndIgnoresLeadingDot() {
        ScannerConfig config = config();

        assertThat(config.isFormatSupported("mp3")).isTrue();
        assertThat(config.isFormatSupported("MP3")).isTrue();
        assertThat(config.isFormatSupported(".flac")).isTrue();
        assertThat(config.isFormatSupported("Wav")).isTrue();
    }

    @Test
    void isFormatSupported_rejectsNullAndUnknown() {
        ScannerConfig config = config();

        assertThat(config.isFormatSupported(null)).isFalse();
        assertThat(config.isFormatSupported("txt")).isFalse();
        assertThat(config.isFormatSupported("")).isFalse();
    }

    @Test
    void defaults_areProductionFriendly() {
        ScannerConfig config = new ScannerConfig();

        assertThat(config.getPaths()).containsExactly("/srv/Music");
        assertThat(config.getSupportedFormats()).contains("mp3", "flac", "aac", "ogg", "m4a", "wav");
        assertThat(config.getScanIntervalSeconds()).isZero();
    }
}
