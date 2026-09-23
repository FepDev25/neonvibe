package com.neonvibe.mapper;

import com.neonvibe.domain.Playlist;
import com.neonvibe.domain.PlaylistTrack;
import com.neonvibe.dto.PlaylistResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps {@link Playlist} and {@link PlaylistTrack} to their response DTOs.
 */
@Mapper(componentModel = "spring")
public interface PlaylistMapper {

    @Mapping(target = "isPublic", source = "public")
    @Mapping(target = "ownerId", source = "userId")
    PlaylistResponse toResponse(Playlist playlist);

    default PlaylistResponse.PlaylistTrackResponse toTrackResponse(PlaylistTrack pt) {
        if (pt == null) {
            return null;
        }
        return new PlaylistResponse.PlaylistTrackResponse(pt.getId(), pt.getTrackId(), pt.getPosition());
    }
}
