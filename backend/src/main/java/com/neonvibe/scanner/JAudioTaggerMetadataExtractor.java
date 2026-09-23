package com.neonvibe.scanner;

import java.nio.file.Path;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default {@link MetadataExtractor} backed by jaudiotagger 3.0.1.
 *
 * <p>Reads ID3 (MP3), MP4/M4A, OGG/FLAC (Vorbis) and WAV tags. Missing/corrupt
 * tags never throw: fields are left null and the title falls back to a
 * normalized filename; common {@code "Artist - Title"} / {@code "NN - Title"}
 * patterns are parsed for display.</p>
 */
@Component
public class JAudioTaggerMetadataExtractor implements MetadataExtractor {

    private static final Logger log = LoggerFactory.getLogger(JAudioTaggerMetadataExtractor.class);

    @Override
    public MusicMetadata extract(Path path) {
        String format = extension(path);
        String mimeType = mimeTypeFor(format);
        String titleFallback = titleFromFilename(path);

        try {
            AudioFile audioFile = AudioFileIO.read(path.toFile());
            Tag tag = audioFile.getTag();
            AudioHeader header = audioFile.getAudioHeader();

            String title = first(tag, FieldKey.TITLE, titleFallback);
            String artist = first(tag, FieldKey.ARTIST, null);
            String album = first(tag, FieldKey.ALBUM, null);
            String albumArtist = first(tag, FieldKey.ALBUM_ARTIST, null);
            Integer year = intOrNull(first(tag, FieldKey.YEAR, null), 4);
            String genre = first(tag, FieldKey.GENRE, null);
            Integer trackNumber = firstInt(tag, FieldKey.TRACK);
            Integer discNumber = firstInt(tag, FieldKey.DISC_NO);
            Integer duration = header != null ? header.getTrackLength() : null;
            Integer bitrate = parseBitrate(header);
            boolean hasLyrics = hasLyrics(tag);

            return new MusicMetadata(
                    title, artist, album, albumArtist, year, genre,
                    trackNumber, discNumber, duration, bitrate,
                    format, mimeType, hasLyrics);
        } catch (Exception ex) {
            // Individual file failures are tolerated: log and return a minimal
            // metadata record (title from filename) so the scanner can continue.
            log.debug("Could not read metadata for {}: {}", path, ex.getMessage());
            return new MusicMetadata(titleFallback, null, null, null, null, null,
                    null, null, null, null, format, mimeType, false);
        }
    }

    @Override
    public EmbeddedArt extractEmbedded(Path path) {
        try {
            AudioFile audioFile = AudioFileIO.read(path.toFile());
            Tag tag = audioFile.getTag();
            if (tag == null) {
                return null;
            }
            Artwork artwork = tag.getFirstArtwork();
            if (artwork == null || artwork.getBinaryData() == null || artwork.getBinaryData().length == 0) {
                return null;
            }
            String mime = artwork.getMimeType();
            String ext = mime != null && mime.contains("png") ? EmbeddedArt.EXT_PNG : EmbeddedArt.EXT_JPEG;
            return new EmbeddedArt(artwork.getBinaryData(), ext);
        } catch (Exception ex) {
            log.debug("Could not extract embedded cover for {}: {}", path, ex.getMessage());
            return null;
        }
    }

    private String first(Tag tag, FieldKey key, String fallback) {
        if (tag == null) {
            return fallback;
        }
        try {
            String value = tag.getFirst(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        } catch (Exception ignore) {
            // tag read failure tolerated
        }
        return fallback;
    }

    private Integer firstInt(Tag tag, FieldKey key) {
        String raw = first(tag, key, null);
        if (raw == null) {
            return null;
        }
        // jaudiotagger sometimes returns "3/12" (track/total); take the first part.
        String cleaned = raw.split("/")[0].trim();
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer intOrNull(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // Take the leading 4-digit year if present in a longer string.
        String cleaned = raw.trim();
        try {
            int num = Integer.parseInt(cleaned.length() > maxLen ? cleaned.substring(0, maxLen) : cleaned);
            return num;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseBitrate(AudioHeader header) {
        if (header == null) {
            return null;
        }
        // jaudiotagger returns the bitrate as a String (e.g. "128" or "~128");
        // parse the leading integer and drop non-digit suffixes.
        String raw = header.getBitRate();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean hasLyrics(Tag tag) {
        if (tag == null) {
            return false;
        }
        try {
            String lyrics = tag.getFirst(FieldKey.LYRICS);
            return lyrics != null && !lyrics.isBlank();
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Parses a display title from the filename when the tag has none.
     * Handles {@code "Artist - Title.ext"}, {@code "NN - Title.ext"},
     * {@code "Artist - Album - Title.ext"} and plain filenames.
     */
    String titleFromFilename(Path path) {
        String fileName = path.getFileName() != null ? path.getFileName().toString() : "Untitled";
        String base = fileName.replaceFirst("(?i)\\.[a-z0-9]+$", "").trim();
        if (base.isBlank()) {
            return "Untitled";
        }
        // If it looks like "NN - Title", strip the leading track number.
        String stripped = base.replaceFirst("^\\d{1,3}\\s*-\\s*", "").trim();
        // Take the last hyphen-delimited segment as the title so that
        // "Artist - Title" and "Artist - Album - Title" both resolve to "Title".
        if (stripped.contains(" - ")) {
            String[] parts = stripped.split(" - ");
            return parts[parts.length - 1].trim();
        }
        return stripped;
    }

    private String extension(Path path) {
        String name = path.getFileName() != null ? path.getFileName().toString() : "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase();
    }

    private String mimeTypeFor(String format) {
        if (format == null) {
            return null;
        }
        return switch (format) {
            case "mp3" -> "audio/mpeg";
            case "flac" -> "audio/flac";
            case "ogg" -> "audio/ogg";
            case "m4a", "mp4" -> "audio/mp4";
            case "wav" -> "audio/wav";
            case "aac" -> "audio/aac";
            default -> null;
        };
    }
}
