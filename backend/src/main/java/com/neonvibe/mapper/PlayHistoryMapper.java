package com.neonvibe.mapper;

import com.neonvibe.domain.PlayHistory;
import com.neonvibe.dto.PlayHistoryResponse;
import org.mapstruct.Mapper;

/**
 * Maps {@link PlayHistory} to its response DTO.
 */
@Mapper(componentModel = "spring")
public interface PlayHistoryMapper {

    PlayHistoryResponse toResponse(PlayHistory history);
}
