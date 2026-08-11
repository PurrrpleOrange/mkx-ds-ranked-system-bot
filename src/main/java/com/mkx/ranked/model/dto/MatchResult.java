package com.mkx.ranked.model.dto;

/**
 * Класс-контейнер для передачи результатов обработки матча из сервиса в UI.
 */
public class MatchResult {
    private final long matchId;
    private final long winnerDiscordId;
    private final long loserDiscordId;
    private final int deltaWinner;
    private final int deltaLoser;
    private final int newWinnerRating;
    private final int newLoserRating;

    public MatchResult(long matchId, long winnerDiscordId, long loserDiscordId,
                       int deltaWinner, int deltaLoser,
                       int newWinnerRating, int newLoserRating) {
        this.matchId = matchId;
        this.winnerDiscordId = winnerDiscordId;
        this.loserDiscordId = loserDiscordId;
        this.deltaWinner = deltaWinner;
        this.deltaLoser = deltaLoser;
        this.newWinnerRating = newWinnerRating;
        this.newLoserRating = newLoserRating;
    }

    public long getMatchId() { return matchId; }
    public long getWinnerDiscordId() { return winnerDiscordId; }
    public long getLoserDiscordId() { return loserDiscordId; }
    public int getDeltaWinner() { return deltaWinner; }
    public int getDeltaLoser() { return deltaLoser; }
    public int getNewWinnerRating() { return newWinnerRating; }
    public int getNewLoserRating() { return newLoserRating; }
}