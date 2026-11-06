package com.neonvibe.scanner;

import java.nio.file.Files;
import java.nio.file.Path;

import com.neonvibe.exception.TagWriteException;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.jaudiotagger.tag.reference.PictureTypes;
import org.springframework.stereotype.Component;

/**
 * Writes editable metadata back to an audio file using jaudiotagger (the same
 * library the scanner reads with, so what is written is what will be read on the
 * next scan).
 *
 * <p>Contract: a {@code null} value leaves the field untouched, an empty/blank
 * string clears it, and any other value is written trimmed. Integer fields are
 * skipped when {@code null}.</p>
 *
 * <p>Write support depends on the container: ID3 (MP3), MP4/M4A, Vorbis
 * (FLAC/OGG) are supported; some containers (e.g. WAV) have limited tag support
 * and may fail, in which case a {@link TagWriteException} is raised.</p>
 */
@Component
public class AudioTagWriter {

    /** The editable tag values for a track. */
    public record TagValues(
            String title,
            String artist,
            String album,
            String albumArtist,
            Integer year,
            String genre,
            Integer trackNumber,
            Integer discNumber) {
    }

    public void write(Path file, TagValues values) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new TagWriteException("Audio file not found: " + file);
        }
        try {
            AudioFile audioFile = AudioFileIO.read(file.toFile());
            Tag tag = audioFile.getTagOrCreateAndSetDefault();
            if (tag == null) {
                tag = audioFile.createDefaultTag();
                audioFile.setTag(tag);
            }

            apply(tag, FieldKey.TITLE, values.title());
            apply(tag, FieldKey.ARTIST, values.artist());
            apply(tag, FieldKey.ALBUM, values.album());
            apply(tag, FieldKey.ALBUM_ARTIST, values.albumArtist());
            apply(tag, FieldKey.YEAR, values.year());
            apply(tag, FieldKey.GENRE, values.genre());
            apply(tag, FieldKey.TRACK, values.trackNumber());
            apply(tag, FieldKey.DISC_NO, values.discNumber());

            AudioFileIO.write(audioFile);
        } catch (TagWriteException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TagWriteException(
                    "Could not write tags to " + file.getFileName() + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Embeds (or replaces) the front cover in an audio file. Used by importers
     * that download a thumbnail alongside the audio; failures are surfaced as
     * {@link TagWriteException} so the caller can decide whether it is fatal.
     */
    public void writeArtwork(Path file, byte[] image, String mimeType) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new TagWriteException("Audio file not found: " + file);
        }
        if (image == null || image.length == 0) {
            return;
        }
        try {
            AudioFile audioFile = AudioFileIO.read(file.toFile());
            Tag tag = audioFile.getTagOrCreateAndSetDefault();
            if (tag == null) {
                tag = audioFile.createDefaultTag();
                audioFile.setTag(tag);
            }
            Artwork artwork = ArtworkFactory.getNew();
            artwork.setBinaryData(image);
            artwork.setMimeType(mimeType == null || mimeType.isBlank()
                    ? "image/jpeg" : mimeType);
            artwork.setDescription("Cover");
            artwork.setPictureType(PictureTypes.DEFAULT_ID);
            tag.setField(artwork);
            AudioFileIO.write(audioFile);
        } catch (Exception ex) {
            throw new TagWriteException(
                    "Could not embed artwork in " + file.getFileName() + ": " + ex.getMessage(), ex);
        }
    }

    private void apply(Tag tag, FieldKey key, String value) throws Exception {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            tag.deleteField(key);
        } else {
            tag.setField(key, trimmed);
        }
    }

    private void apply(Tag tag, FieldKey key, Integer value) throws Exception {
        if (value == null) {
            return;
        }
        tag.setField(key, String.valueOf(value));
    }
}
