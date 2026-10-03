package com.neonvibe.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.neonvibe.domain.Album;
import com.neonvibe.domain.Artist;
import com.neonvibe.dto.AlbumResponse;
import com.neonvibe.dto.ArtistResponse;
import com.neonvibe.dto.TrackResponse;
import com.neonvibe.exception.ResourceNotFoundException;
import com.neonvibe.mapper.AlbumMapper;
import com.neonvibe.mapper.ArtistMapper;
import com.neonvibe.mapper.TrackMapper;
import com.neonvibe.repository.AlbumRepository;
import com.neonvibe.repository.ArtistRepository;
import com.neonvibe.repository.TrackRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read access to {@link Artist} and its associated albums/tracks.
 */
@Service
public class ArtistService {

    /** Stable, case-insensitive order by artist name. */
    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.asc("name").ignoreCase(),
            Sort.Order.asc("id"));

    private final ArtistRepository artistRepository;
    private final TrackRepository trackRepository;
    private final AlbumRepository albumRepository;
    private final ArtistMapper artistMapper;
    private final TrackMapper trackMapper;
    private final AlbumMapper albumMapper;

    public ArtistService(ArtistRepository artistRepository,
                         TrackRepository trackRepository,
                         AlbumRepository albumRepository,
                         ArtistMapper artistMapper,
                         TrackMapper trackMapper,
                         AlbumMapper albumMapper) {
        this.artistRepository = artistRepository;
        this.trackRepository = trackRepository;
        this.albumRepository = albumRepository;
        this.artistMapper = artistMapper;
        this.trackMapper = trackMapper;
        this.albumMapper = albumMapper;
    }

    @Transactional(readOnly = true)
    public Page<ArtistResponse> search(String q, Pageable pageable) {
        Pageable sorted = PageableSorts.withDefault(pageable, DEFAULT_SORT);
        Page<Artist> page;
        if (q != null && !q.isBlank()) {
            page = artistRepository.findByNameContainingIgnoreCase(q.trim(), sorted);
        } else {
            page = artistRepository.findAll(sorted);
        }
        List<ArtistResponse> dtos = page.getContent().stream().map(artistMapper::toResponse).toList();
        return new PageImpl<>(dtos, sorted, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ArtistResponse getById(Long id) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Artist not found: " + id));
        return artistMapper.toResponse(artist);
    }

    @Transactional(readOnly = true)
    public List<AlbumResponse> getAlbums(Long id) {
        Artist artist = require(id);
        String name = artist.getName();
        // Exact match (case-insensitive): Album.artist is a denormalized string
        // set to the track artist name by the scanner.
        List<Album> albums = albumRepository.findByArtistIgnoreCase(name);
        Map<Long, Long> counts = countsFor(albums);
        return albums.stream().map(album -> {
            AlbumResponse r = albumMapper.toResponse(album);
            return new AlbumResponse(r.id(), r.name(), r.artist(), r.year(),
                    r.genre(), r.coverArtPath(), r.createdAt(),
                    counts.getOrDefault(album.getId(), 0L));
        }).toList();
    }

    /** One grouped COUNT query for all the artist's albums instead of one each. */
    private Map<Long, Long> countsFor(List<Album> albums) {
        List<Long> ids = albums.stream().map(Album::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return trackRepository.countAvailableByAlbumIds(ids).stream()
                .collect(Collectors.toMap(TrackRepository.AlbumTrackCount::getAlbumId,
                        TrackRepository.AlbumTrackCount::getTrackCount));
    }

    @Transactional(readOnly = true)
    public List<TrackResponse> getTracks(Long id) {
        require(id);
        return trackRepository.findByArtistEntityId(id).stream().map(trackMapper::toResponse).toList();
    }

    /** Batch lookup used by favorites. */
    @Transactional(readOnly = true)
    public List<ArtistResponse> findByIds(Collection<Long> ids) {
        return artistRepository.findAllById(ids).stream().map(artistMapper::toResponse).toList();
    }

    private Artist require(Long id) {
        return artistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Artist not found: " + id));
    }
}
