package com.mkx.ranked.model;

import jakarta.persistence.*;

/**
 * Сущность участия игрока в конкретном рейтинговом сезоне.
 *
 * <p>Хранит сезонное состояние игрока:
 * рейтинг, количество сыгранных матчей и итоговое место.</p>
 *
 * <p>Постоянные данные игрока хранятся в {@link PlayerEntity},
 * а общая информация о сезоне — в {@link SeasonEntity}.</p>
 */
@Entity
@Table(
        name = "season_players",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_season_player",
                        columnNames = {"season_id", "player_id"}
                )
        }
)
public class SeasonPlayerEntity {

    /**
     * Внутренний идентификатор записи участия игрока в сезоне.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Игрок, участвующий в сезоне.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerEntity player;

    /**
     * Сезон, в котором участвует игрок.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private SeasonEntity season;

    /**
     * Текущий рейтинг игрока в рамках сезона.
     */
    @Column(name = "rating", nullable = false)
    private Integer rating = 1000;

    /**
     * Количество сыгранных матчей в рамках сезона.
     */
    @Column(name = "games_played", nullable = false)
    private Integer gamesPlayed = 0;

    /**
     * Итоговое место игрока после завершения сезона.
     *
     * <p>Пока сезон не завершён, имеет значение {@code null}.</p>
     */
    @Column(name = "final_rank")
    private Integer finalRank;

    /**
     * Конструктор без параметров, необходимый JPA.
     */
    public SeasonPlayerEntity() {
    }

    /**
     * Создаёт запись участия игрока в сезоне
     * со стартовым рейтингом 1000 и нулём сыгранных матчей.
     *
     * @param player игрок
     * @param season сезон
     */
    public SeasonPlayerEntity(PlayerEntity player, SeasonEntity season) {
        this.player = player;
        this.season = season;
        this.rating = 1000;
        this.gamesPlayed = 0;
    }

    /**
     * @return внутренний идентификатор записи
     */
    public Long getId() {
        return id;
    }

    /**
     * @return игрок
     */
    public PlayerEntity getPlayer() {
        return player;
    }

    /**
     * @param player игрок
     */
    public void setPlayer(PlayerEntity player) {
        this.player = player;
    }

    /**
     * @return сезон
     */
    public SeasonEntity getSeason() {
        return season;
    }

    /**
     * @param season сезон
     */
    public void setSeason(SeasonEntity season) {
        this.season = season;
    }

    /**
     * @return текущий рейтинг игрока в сезоне
     */
    public Integer getRating() {
        return rating;
    }

    /**
     * @param rating новый рейтинг игрока
     */
    public void setRating(Integer rating) {
        this.rating = rating;
    }

    /**
     * @return количество сыгранных матчей
     */
    public Integer getGamesPlayed() {
        return gamesPlayed;
    }

    /**
     * @param gamesPlayed новое количество сыгранных матчей
     */
    public void setGamesPlayed(Integer gamesPlayed) {
        this.gamesPlayed = gamesPlayed;
    }

    /**
     * @return итоговое место игрока или {@code null},
     * если сезон ещё не завершён
     */
    public Integer getFinalRank() {
        return finalRank;
    }

    /**
     * @param finalRank итоговое место игрока
     */
    public void setFinalRank(Integer finalRank) {
        this.finalRank = finalRank;
    }
}
