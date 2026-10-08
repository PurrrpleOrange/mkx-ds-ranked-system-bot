package com.mkx.ranked.model.dto;

import java.time.LocalDateTime;

public record PlayerProfileDto(
        long playerId,
        Long discordId,
        String displayName,
        int rating,
        int gamesPlayed,
        Integer rank,
        String tierName,
        String tierEmoji,
        SeasonDto season,
        LocalDateTime removedAt,
        Long removedBy
) {
    public boolean removed() {
        return removedAt != null;
    }
}
