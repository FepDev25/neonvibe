package com.neonvibe.scanner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryCatalogTest {

    @Test
    void normalizeAlbumName_blankBecomesSingles() {
        assertThat(LibraryCatalog.normalizeAlbumName(null)).isEqualTo("Singles");
        assertThat(LibraryCatalog.normalizeAlbumName("")).isEqualTo("Singles");
        assertThat(LibraryCatalog.normalizeAlbumName("   ")).isEqualTo("Singles");
    }

    @Test
    void normalizeAlbumName_legacySingleTagsBecomeSingles() {
        assertThat(LibraryCatalog.normalizeAlbumName("Single")).isEqualTo("Singles");
        assertThat(LibraryCatalog.normalizeAlbumName("singles")).isEqualTo("Singles");
        assertThat(LibraryCatalog.normalizeAlbumName("  SINGLES  ")).isEqualTo("Singles");
    }

    @Test
    void normalizeAlbumName_keepsRealAlbumsAndTrims() {
        assertThat(LibraryCatalog.normalizeAlbumName("  Seasons End ")).isEqualTo("Seasons End");
        assertThat(LibraryCatalog.normalizeAlbumName("Singles Collection")).isEqualTo("Singles Collection");
    }

    @Test
    void isSingles_matchesOnlyTheReservedName() {
        assertThat(LibraryCatalog.isSingles("Singles")).isTrue();
        assertThat(LibraryCatalog.isSingles(" SINGLES ")).isTrue();
        assertThat(LibraryCatalog.isSingles("Singles Collection")).isFalse();
        assertThat(LibraryCatalog.isSingles(null)).isFalse();
    }

    @Test
    void isReservedSingles_requiresNoArtist() {
        assertThat(LibraryCatalog.isReservedSingles("Singles", null)).isTrue();
        assertThat(LibraryCatalog.isReservedSingles("Singles", "Artist")).isFalse();
        assertThat(LibraryCatalog.isReservedSingles("Album", null)).isFalse();
    }
}
