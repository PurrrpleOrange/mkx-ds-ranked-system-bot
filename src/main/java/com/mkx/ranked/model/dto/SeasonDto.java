package com.mkx.ranked.model.dto;

import com.mkx.ranked.model.enums.SeasonStatus;

import java.time.LocalDateTime;

public record SeasonDto(
        long id,
        int seasonNumber,
        String name,
        SeasonStatus status,
        LocalDateTime startDate,
        LocalDateTime plannedEndDate,
        LocalDateTime endDate
) {
}
