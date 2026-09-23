package com.neonvibe.scanner;

/**
 * Artwork embedded in an audio file's tags (ID3 APIC, Vorbis COVERART, MP4 covr).
 */
public record EmbeddedArt(byte[] data, String extension) {

    public static final String EXT_JPEG = "jpg";
    public static final String EXT_PNG = "png";
}
