package com.mkx.ranked.model;

import jakarta.persistence.*;

@Entity
@Table(name = "players")
public class PlayerEntity {

    @Id
    @Column(name = "discord_id")
    private Long discordId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "rating", nullable = false)
    private Integer rating = 1000;

    @Column(name = "games_played", nullable = false)
    private Integer gamesPlayed = 0;

    public PlayerEntity() {}

    public PlayerEntity(Long discordId, String username) {
        this.discordId = discordId;
        this.username = username;
        this.displayName = username;
    }

    // Getters and Setters
    public Long getDiscordId() { return discordId; }
    public void setDiscordId(Long discordId) { this.discordId = discordId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public Integer getGamesPlayed() { return gamesPlayed; }
    public void setGamesPlayed(Integer gamesPlayed) { this.gamesPlayed = gamesPlayed; }
}