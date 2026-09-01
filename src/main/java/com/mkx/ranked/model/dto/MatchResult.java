package com.mkx.ranked.model.dto;

/**
 * Result returned by the service layer after a ranked match is persisted.
 */
public record MatchResult(
        long matchId,
        long winnerDiscordId,
        String winnerDisplayName,
        long loserDiscordId,
        String loserDisplayName,
        int winnerScore,
        int loserScore,
        int deltaWinner,
        int deltaLoser,
        int newWinnerRating,
        int newLoserRating
) {
}
