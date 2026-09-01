package com.mkx.ranked.model.dto;

import java.time.LocalDateTime;

public record AdminMatchDto(
        long matchId,
        int seasonNumber,
        String winnerDisplayName,
        String loserDisplayName,
        long winnerDiscordId,
        long loserDiscordId,
        int winnerScore,
        int loserScore,
        int deltaWinner,
        int deltaLoser,
        LocalDateTime createdAt
) {
}
