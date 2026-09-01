package com.mkx.ranked.model;

import jakarta.persistence.*;

/**
 * Сущность игрока.
 *
 * <p>Хранит Discord identity и актуальные данные Discord-профиля.
 * Игровой ник хранится как снимок в {@link SeasonPlayerEntity}.</p>
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
     * <p>Это единственный стабильный внешний идентификатор игрока.</p>
     */
    @Column(name = "discord_id", unique = true)
    private Long discordId;

    /**
     * Актуальный Discord username. Не используется как identity.
     */
    @Column(name = "username", nullable = false)
    private String username;

    /**
     * Конструктор без параметров, необходимый JPA.
     */
    public PlayerEntity() {
    }

    /**
     * Создаёт постоянный профиль Discord-пользователя.
     *
     * @param discordId Discord ID пользователя
     * @param username актуальный Discord username
     */
    public PlayerEntity(Long discordId, String username) {
        this.discordId = discordId;
        this.username = username;
    }

    /**
     * @return внутренний идентификатор игрока
     */
    public Long getId() {
        return id;
    }

    /**
     * @return Discord ID игрока
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
     * @return актуальный Discord username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param username новый Discord username
     */
    public void setUsername(String username) {
        this.username = username;
    }

}
