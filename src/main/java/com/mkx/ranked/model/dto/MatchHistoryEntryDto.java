package com.mkx.ranked.model.dto;

import java.time.LocalDateTime;

public record MatchHistoryEntryDto(
        long matchId,
        boolean win,
        String opponentDisplayName,
        int scoreFor,
        int scoreAgainst,
        int ratingDelta,
        LocalDateTime createdAt
) {
}
