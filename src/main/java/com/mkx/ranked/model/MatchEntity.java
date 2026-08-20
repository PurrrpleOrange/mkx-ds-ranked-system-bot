package com.mkx.ranked.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Сущность рейтингового матча.
 *
 * <p>Хранит результат матча, изменение рейтинга участников
 * и принадлежность матча к конкретному сезону.</p>
 *
 * <p>Победитель и проигравший представлены через
 * {@link SeasonPlayerEntity}, поскольку рейтинг игрока
 * существует в рамках конкретного сезона.</p>
 */
@Entity
@Table(name = "matches")
public class MatchEntity {

    /**
     * Внутренний идентификатор матча.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Сезон, в рамках которого был сыгран матч.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private SeasonEntity season;

    /**
     * Победитель матча.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "winner_id", nullable = false)
    private SeasonPlayerEntity winner;

    /**
     * Проигравший матча.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loser_id", nullable = false)
    private SeasonPlayerEntity loser;

    /**
     * Количество выигранных раундов победителем.
     */
    @Column(name = "winner_score", nullable = false)
    private Integer winnerScore;

    /**
     * Количество выигранных раундов проигравшим.
     */
    @Column(name = "loser_score", nullable = false)
    private Integer loserScore;

    /**
     * Изменение рейтинга победителя после матча.
     */
    @Column(name = "delta_winner", nullable = false)
    private Integer deltaWinner;

    /**
     * Изменение рейтинга проигравшего после матча.
     */
    @Column(name = "delta_loser", nullable = false)
    private Integer deltaLoser;

    /**
     * Дата и время создания матча.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Конструктор без параметров, необходимый JPA.
     */
    public MatchEntity() {
    }

    /**
     * Создаёт новый рейтинговый матч.
     *
     * @param season сезон
     * @param winner победитель матча
     * @param loser проигравший матча
     * @param winnerScore счёт победителя
     * @param loserScore счёт проигравшего
     * @param deltaWinner изменение рейтинга победителя
     * @param deltaLoser изменение рейтинга проигравшего
     */
    public MatchEntity(
            SeasonEntity season,
            SeasonPlayerEntity winner,
            SeasonPlayerEntity loser,
            Integer winnerScore,
            Integer loserScore,
            Integer deltaWinner,
            Integer deltaLoser
    ) {
        this.season = season;
        this.winner = winner;
        this.loser = loser;
        this.winnerScore = winnerScore;
        this.loserScore = loserScore;
        this.deltaWinner = deltaWinner;
        this.deltaLoser = deltaLoser;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public SeasonEntity getSeason() {
        return season;
    }

    public void setSeason(SeasonEntity season) {
        this.season = season;
    }

    public SeasonPlayerEntity getWinner() {
        return winner;
    }

    public void setWinner(SeasonPlayerEntity winner) {
        this.winner = winner;
    }

    public SeasonPlayerEntity getLoser() {
        return loser;
    }

    public void setLoser(SeasonPlayerEntity loser) {
        this.loser = loser;
    }

    public Integer getWinnerScore() {
        return winnerScore;
    }

    public void setWinnerScore(Integer winnerScore) {
        this.winnerScore = winnerScore;
    }

    public Integer getLoserScore() {
        return loserScore;
    }

    public void setLoserScore(Integer loserScore) {
        this.loserScore = loserScore;
    }

    public Integer getDeltaWinner() {
        return deltaWinner;
    }

    public void setDeltaWinner(Integer deltaWinner) {
        this.deltaWinner = deltaWinner;
    }

    public Integer getDeltaLoser() {
        return deltaLoser;
    }

    public void setDeltaLoser(Integer deltaLoser) {
        this.deltaLoser = deltaLoser;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}