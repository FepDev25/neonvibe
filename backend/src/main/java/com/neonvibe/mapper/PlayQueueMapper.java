package com.neonvibe.mapper;

import com.neonvibe.domain.PlayQueue;
import com.neonvibe.dto.PlayQueueResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps {@link PlayQueue} to its response DTO. The {@code tracksOrder} JSON string
 * is decoded by the service (Jackson) and passed in via a custom method.
 */
@Mapper(componentModel = "spring")
public interface PlayQueueMapper {

    @Mapping(target = "tracksOrder", ignore = true)
    PlayQueueResponse toResponse(PlayQueue queue);
}
