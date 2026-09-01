package com.mkx.ranked.model.dto;

import java.util.List;

public record AdminSeasonStatisticsDto(
        SeasonDto season,
        long playerCount,
        long matchCount,
        int averageRating,
        List<LeaderboardEntryDto> topPlayers
) {
}
