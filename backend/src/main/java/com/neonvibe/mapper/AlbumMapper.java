package com.neonvibe.mapper;

import com.neonvibe.domain.Album;
import com.neonvibe.dto.AlbumResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps {@link Album} -> {@link AlbumResponse}. The {@code trackCount} field is
 * populated by callers (derived from the lazy collection size) rather than in
 * the mapper to avoid loading the collection during simple DTO mapping.
 */
@Mapper(componentModel = "spring")
public interface AlbumMapper {

    @Mapping(target = "trackCount", ignore = true)
    AlbumResponse toResponse(Album album);
}
