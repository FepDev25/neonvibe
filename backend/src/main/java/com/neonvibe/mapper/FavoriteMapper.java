package com.neonvibe.mapper;

import com.neonvibe.domain.Favorite;
import com.neonvibe.dto.FavoriteResponse;
import org.mapstruct.Mapper;

/**
 * Maps {@link Favorite} to its response DTO.
 */
@Mapper(componentModel = "spring")
public interface FavoriteMapper {

    FavoriteResponse toResponse(Favorite favorite);
}
