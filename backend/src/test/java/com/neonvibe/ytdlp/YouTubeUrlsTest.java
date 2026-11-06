package com.neonvibe.ytdlp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YouTubeUrlsTest {

    @Test
    void acceptsSupportedYouTubeHosts() {
        assertThat(YouTubeUrls.requireValid(" https://www.youtube.com/watch?v=abc123 "))
                .isEqualTo("https://www.youtube.com/watch?v=abc123");
        assertThat(YouTubeUrls.requireValid("https://youtu.be/abc123")).contains("youtu.be");
        assertThat(YouTubeUrls.requireValid("https://music.youtube.com/watch?v=abc123"))
                .contains("music.youtube.com");
    }

    @Test
    void rejectsNonYouTubeHostsToAvoidSsrf() {
        assertThatThrownBy(() -> YouTubeUrls.requireValid("https://youtube.com.evil.example/watch?v=1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YouTube");
        assertThatThrownBy(() -> YouTubeUrls.requireValid("https://example.com/video"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonHttpsUrls() {
        assertThatThrownBy(() -> YouTubeUrls.requireValid("http://youtube.com/watch?v=1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
    }

    @Test
    void rejectsBlankAndMalformedValues() {
        assertThatThrownBy(() -> YouTubeUrls.requireValid("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> YouTubeUrls.requireValid("not a url"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
