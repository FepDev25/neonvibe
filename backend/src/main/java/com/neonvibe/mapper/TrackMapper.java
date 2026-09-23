package com.neonvibe.mapper;

import com.neonvibe.domain.Track;
import com.neonvibe.dto.TrackResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps {@link Track} <-> DTOs.
 */
@Mapper(componentModel = "spring")
public interface TrackMapper {

    @Mapping(target = "isAvailable", source = "available")
    TrackResponse toResponse(Track track);
}
