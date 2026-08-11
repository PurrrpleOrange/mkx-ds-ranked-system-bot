package com.mkx.ranked.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "seasons")
public class SeasonEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "season_number", unique = true, nullable = false)
    private Integer seasonNumber;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "planned_end_date")
    private LocalDateTime plannedEndDate; // 👈 Планируемая дата окончания сезона

    @Column(name = "end_date")
    private LocalDateTime endDate; // Фактическая дата закрытия

    public SeasonEntity() {
        this.startDate = LocalDateTime.now();
    }

    public SeasonEntity(Integer seasonNumber, String name, LocalDateTime plannedEndDate) {
        this.seasonNumber = seasonNumber;
        this.name = name;
        this.startDate = LocalDateTime.now();
        this.plannedEndDate = plannedEndDate;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public Integer getSeasonNumber() { return seasonNumber; }
    public void setSeasonNumber(Integer seasonNumber) { this.seasonNumber = seasonNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }

    public LocalDateTime getPlannedEndDate() { return plannedEndDate; }
    public void setPlannedEndDate(LocalDateTime plannedEndDate) { this.plannedEndDate = plannedEndDate; }

    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
}