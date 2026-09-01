package com.mkx.ranked.model.dto;

import java.time.LocalDateTime;

public record MatchHistoryEntryDto(
        boolean win,
        String opponentDisplayName,
        int scoreFor,
        int scoreAgainst,
        int ratingDelta,
        LocalDateTime createdAt
) {
}
