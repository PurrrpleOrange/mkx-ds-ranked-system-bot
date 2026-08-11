package com.mkx.ranked.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "matches")
public class MatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id", nullable = false)
    private PlayerEntity winner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loser_id", nullable = false)
    private PlayerEntity loser;

    @Column(name = "winner_score", nullable = false)
    private Integer winnerScore;

    @Column(name = "loser_score", nullable = false)
    private Integer loserScore;

    @Column(name = "delta_winner", nullable = false)
    private Integer deltaWinner;

    @Column(name = "delta_loser", nullable = false)
    private Integer deltaLoser;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public MatchEntity() {}

    // Getters and Setters
    public Long getId() { return id; }
    public PlayerEntity getWinner() { return winner; }
    public void setWinner(PlayerEntity winner) { this.winner = winner; }
    public PlayerEntity getLoser() { return loser; }
    public void setLoser(PlayerEntity loser) { this.loser = loser; }
    public Integer getWinnerScore() { return winnerScore; }
    public void setWinnerScore(Integer winnerScore) { this.winnerScore = winnerScore; }
    public Integer getLoserScore() { return loserScore; }
    public void setLoserScore(Integer loserScore) { this.loserScore = loserScore; }
    public Integer getDeltaWinner() { return deltaWinner; }
    public void setDeltaWinner(Integer deltaWinner) { this.deltaWinner = deltaWinner; }
    public Integer getDeltaLoser() { return deltaLoser; }
    public void setDeltaLoser(Integer deltaLoser) { this.deltaLoser = deltaLoser; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}