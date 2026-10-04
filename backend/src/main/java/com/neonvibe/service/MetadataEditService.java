package com.neonvibe.service;

import java.nio.file.Path;
import java.util.List;

import com.neonvibe.domain.Album;
import com.neonvibe.domain.Artist;
import com.neonvibe.domain.Track;
import com.neonvibe.dto.AlbumMetadataRequest;
import com.neonvibe.dto.ArtistMetadataRequest;
import com.neonvibe.dto.TrackMetadataRequest;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.exception.TagWriteException;
import com.neonvibe.mapper.TrackMapper;
import com.neonvibe.repository.AlbumRepository;
import com.neonvibe.repository.ArtistRepository;
import com.neonvibe.repository.TrackRepository;
import com.neonvibe.scanner.AudioTagWriter;
import com.neonvibe.scanner.LibrarySyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edits library metadata and writes it back to the real audio files (the DB is
 * updated from the same values, so a later scan reads a consistent state).
 *
 * <p>Track edits are all-or-nothing (a single file write failure aborts the
 * request). Album/artist edits propagate to every affected file on a
 * best-effort basis: files that cannot be written are skipped and logged, the
 * rest are persisted; if <em>no</em> file could be written the request fails.</p>
 */
@Service
public class MetadataEditService {

    private static final Logger log = LoggerFactory.getLogger(MetadataEditService.class);

    private final TrackRepository trackRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final AudioTagWriter tagWriter;
    private final LibrarySyncService librarySyncService;
    private final TrackMapper trackMapper;

    public MetadataEditService(TrackRepository trackRepository,
                               AlbumRepository albumRepository,
                               ArtistRepository artistRepository,
                               AudioTagWriter tagWriter,
                               LibrarySyncService librarySyncService,
                               TrackMapper trackMapper) {
        this.trackRepository = trackRepository;
        this.albumRepository = albumRepository;
        this.artistRepository = artistRepository;
        this.tagWriter = tagWriter;
        this.librarySyncService = librarySyncService;
        this.trackMapper = trackMapper;
    }

    /** Outcome of a propagation edit: how many files were written vs skipped. */
    public record PropagationResult(Long id, int updated, int failed) {
    }

    @Transactional
    public TrackResponse updateTrack(Long id, TrackMetadataRequest request) {
        Track track = trackRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + id));

        writeTags(track, request.title(), request.artist(), request.album(),
                request.albumArtist(), request.year(), request.genre(),
                request.trackNumber(), request.discNumber());
        applyTrackFields(track, request.title(), request.artist(), request.album(),
                request.albumArtist(), request.year(), request.genre(),
                request.trackNumber(), request.discNumber());

        return trackMapper.toResponse(trackRepository.save(track));
    }

    @Transactional
    public PropagationResult updateAlbum(Long id, AlbumMetadataRequest request) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Album not found: " + id));

        String newName = request.name().trim();
        Integer year = request.year();
        String genre = trimToNull(request.genre());

        boolean renamed = !album.getName().equals(newName);
        Album target = album;
        if (renamed) {
            String artist = album.getArtist();
            target = albumRepository.findByNameAndArtistNullSafe(newName, artist)
                    .orElseGet(() -> {
                        Album created = new Album();
                        created.setName(newName);
                        created.setArtist(artist);
                        return albumRepository.save(created);
                    });
        }
        target.setYear(year);
        target.setGenre(genre);
        target = albumRepository.save(target);

        int updated = 0;
        int failed = 0;
        for (Track track : trackRepository.findByAlbumEntityId(id)) {
            try {
                writeTags(track, track.getTitle(), track.getArtist(), newName,
                        track.getAlbumArtist(), year, genre,
                        track.getTrackNumber(), track.getDiscNumber());
                track.setAlbum(newName);
                track.setYear(year);
                track.setGenre(genre);
                track.setAlbumEntity(target);
                trackRepository.save(track);
                updated++;
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Album metadata edit: could not update track {}: {}", track.getId(), ex.getMessage());
            }
        }
        if (failed > 0 && updated == 0) {
            throw new TagWriteException(
                    "No se pudo escribir en ningún fichero del álbum (¿permisos de escritura?)");
        }
        deleteIfEmpty(album, target);

        return new PropagationResult(target.getId(), updated, failed);
    }

    @Transactional
    public PropagationResult updateArtist(Long id, ArtistMetadataRequest request) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Artist not found: " + id));

        String oldName = artist.getName();
        String newName = request.name().trim();
        if (oldName.equals(newName)) {
            return new PropagationResult(id, 0, 0);
        }
        Artist target = artistRepository.findByName(newName)
                .orElseGet(() -> {
                    Artist created = new Artist();
                    created.setName(newName);
                    return artistRepository.save(created);
                });

        int updated = 0;
        int failed = 0;
        for (Track track : trackRepository.findByArtistEntityId(id)) {
            String albumArtist = equalsIgnoreCase(track.getAlbumArtist(), oldName)
                    ? newName : track.getAlbumArtist();
            try {
                writeTags(track, track.getTitle(), newName, track.getAlbum(), albumArtist,
                        track.getYear(), track.getGenre(),
                        track.getTrackNumber(), track.getDiscNumber());
                track.setArtist(newName);
                track.setAlbumArtist(albumArtist);
                track.setArtistEntity(target);
                trackRepository.save(track);
                updated++;
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Artist metadata edit: could not update track {}: {}", track.getId(), ex.getMessage());
            }
        }
        // Album.artist is a denormalized string used to list an artist's albums:
        // keep it in sync or the renamed artist's discography would look empty.
        for (Album album : albumRepository.findByArtistIgnoreCase(oldName)) {
            album.setArtist(newName);
            albumRepository.save(album);
        }
        if (failed > 0 && updated == 0) {
            throw new TagWriteException(
                    "No se pudo renombrar el artista en ningún fichero (¿permisos de escritura?)");
        }
        deleteIfEmpty(artist, target);

        return new PropagationResult(target.getId(), updated, failed);
    }

    private void applyTrackFields(Track track, String title, String artist, String album,
                                  String albumArtist, Integer year, String genre,
                                  Integer trackNumber, Integer discNumber) {
        String artistName = trimToNull(artist);
        String albumName = trimToNull(album);
        String genreName = trimToNull(genre);

        track.setTitle(title);
        track.setArtist(artistName);
        track.setAlbum(albumName);
        track.setAlbumArtist(trimToNull(albumArtist));
        track.setYear(year);
        track.setGenre(genreName);
        track.setTrackNumber(trackNumber);
        track.setDiscNumber(discNumber);
        // Denormalized FKs are re-derived from the new names, exactly like a scan.
        track.setArtistEntity(librarySyncService.resolveArtist(artistName));
        track.setAlbumEntity(librarySyncService.resolveAlbum(albumName, artistName, year, genreName));
    }

    private void writeTags(Track track, String title, String artist, String album,
                           String albumArtist, Integer year, String genre,
                           Integer trackNumber, Integer discNumber) {
        tagWriter.write(Path.of(track.getFilePath()), new AudioTagWriter.TagValues(
                title, artist, album, albumArtist, year, genre, trackNumber, discNumber));
    }

    /** Removes the source album/artist after a rename if no track references it anymore. */
    private void deleteIfEmpty(Album source, Album target) {
        if (source.getId().equals(target.getId())) {
            return;
        }
        if (trackRepository.findByAlbumEntityId(source.getId()).isEmpty()) {
            albumRepository.delete(source);
        }
    }

    private void deleteIfEmpty(Artist source, Artist target) {
        if (source.getId().equals(target.getId())) {
            return;
        }
        if (trackRepository.findByArtistEntityId(source.getId()).isEmpty()) {
            artistRepository.delete(source);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }
}
