package com.neonvibe.mapper;

import com.neonvibe.domain.Artist;
import com.neonvibe.dto.ArtistResponse;
import org.mapstruct.Mapper;

/**
 * Maps {@link Artist} -> {@link ArtistResponse}.
 */
@Mapper(componentModel = "spring")
public interface ArtistMapper {

    ArtistResponse toResponse(Artist artist);
}
