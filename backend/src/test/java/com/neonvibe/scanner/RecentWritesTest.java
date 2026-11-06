package com.neonvibe.scanner;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecentWritesTest {

    private final RecentWrites recentWrites = new RecentWrites();

    @Test
    void markedPath_isRecentForAnEquivalentPath() {
        recentWrites.mark(Path.of("/srv/Music/a.mp3"));

        assertThat(recentWrites.isRecent(Path.of("/srv/Music/a.mp3"))).isTrue();
        assertThat(recentWrites.isRecent(Path.of("/srv/Music/b.mp3"))).isFalse();
    }

    @Test
    void unmarkedOrNullPath_isNotRecent() {
        assertThat(recentWrites.isRecent(Path.of("/srv/Music/a.mp3"))).isFalse();
        assertThat(recentWrites.isRecent(null)).isFalse();
        recentWrites.mark(null);
    }
}
