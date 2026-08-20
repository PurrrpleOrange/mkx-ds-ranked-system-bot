package com.mkx.ranked.model;

import jakarta.persistence.*;

/**
 * Сущность игрока.
 *
 * <p>Хранит постоянные данные игрока, которые не зависят
 * от конкретного рейтингового сезона.</p>
 *
 * <p>Сезонные данные, такие как рейтинг и количество сыгранных матчей,
 * должны храниться отдельно в сущности {@code SeasonPlayerEntity}.</p>
 */
@Entity
@Table(name = "players")
public class PlayerEntity {

    /**
     * Внутренний идентификатор игрока в базе данных.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Discord ID пользователя.
     *
     * <p>Может быть {@code null}, если игрок ещё не прошёл регистрацию
     * и не привязал Discord-аккаунт.</p>
     */
    @Column(name = "discord_id", unique = true)
    private Long discordId;

    /**
     * Игровой никнейм игрока.
     */
    @Column(name = "username", nullable = false)
    private String username;

    /**
     * Отображаемое имя игрока.
     */
    @Column(name = "display_name", nullable = false)
    private String displayName;

    /**
     * Конструктор без параметров, необходимый JPA.
     */
    public PlayerEntity() {
    }

    /**
     * Создаёт игрока без привязанного Discord-аккаунта.
     *
     * @param username игровой никнейм игрока
     * @param displayName отображаемое имя игрока
     */
    public PlayerEntity(String username, String displayName) {
        this.username = username;
        this.displayName = displayName;
    }

    /**
     * @return внутренний идентификатор игрока
     */
    public Long getId() {
        return id;
    }

    /**
     * @return Discord ID игрока или {@code null},
     * если Discord-аккаунт ещё не привязан
     */
    public Long getDiscordId() {
        return discordId;
    }

    /**
     * Устанавливает Discord ID игрока.
     *
     * @param discordId Discord ID пользователя
     */
    public void setDiscordId(Long discordId) {
        this.discordId = discordId;
    }

    /**
     * @return игровой никнейм игрока
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param username новый игровой никнейм игрока
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return отображаемое имя игрока
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @param displayName новое отображаемое имя игрока
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}