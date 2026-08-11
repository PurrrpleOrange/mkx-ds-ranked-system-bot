package com.mkx.ranked.model;

import jakarta.persistence.*;

@Entity
@Table(name = "season_history")
public class SeasonHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private SeasonEntity season;

    @Column(name = "discord_id")
    private Long discordId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "final_rating", nullable = false)
    private Integer finalRating;

    @Column(name = "games_played", nullable = false)
    private Integer gamesPlayed;

    @Column(name = "final_rank", nullable = false)
    private Integer finalRank;

    public SeasonHistoryEntity() {}

    // Getters and Setters
    public Long getId() { return id; }
    public SeasonEntity getSeason() { return season; }
    public void setSeason(SeasonEntity season) { this.season = season; }
    public Long getDiscordId() { return discordId; }
    public void setDiscordId(Long discordId) { this.discordId = discordId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getFinalRating() { return finalRating; }
    public void setFinalRating(Integer finalRating) { this.finalRating = finalRating; }
    public Integer getGamesPlayed() { return gamesPlayed; }
    public void setGamesPlayed(Integer gamesPlayed) { this.gamesPlayed = gamesPlayed; }
    public Integer getFinalRank() { return finalRank; }
    public void setFinalRank(Integer finalRank) { this.finalRank = finalRank; }
}