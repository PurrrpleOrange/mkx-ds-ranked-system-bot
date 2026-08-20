package com.mkx.ranked.model;

import com.mkx.ranked.model.enums.SeasonStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Сущность рейтингового сезона.
 *
 * <p>Хранит общую информацию о сезоне и его жизненном цикле.
 * Данные конкретных игроков внутри сезона должны храниться
 * в {@code SeasonPlayerEntity}.</p>
 */
@Entity
@Table(name = "seasons")
public class SeasonEntity {

    /**
     * Внутренний идентификатор сезона.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Порядковый номер сезона.
     */
    @Column(name = "season_number", unique = true, nullable = false)
    private Integer seasonNumber;

    /**
     * Отображаемое название сезона.
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Текущий статус сезона.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SeasonStatus status = SeasonStatus.CREATED;

    /**
     * Фактическая дата и время запуска сезона.
     *
     * <p>До запуска сезона может быть {@code null}.</p>
     */
    @Column(name = "start_date")
    private LocalDateTime startDate;

    /**
     * Планируемая дата окончания сезона.
     */
    @Column(name = "planned_end_date")
    private LocalDateTime plannedEndDate;

    /**
     * Фактическая дата и время завершения сезона.
     *
     * <p>До завершения сезона имеет значение {@code null}.</p>
     */
    @Column(name = "end_date")
    private LocalDateTime endDate;

    /**
     * Конструктор без параметров, необходимый JPA.
     */
    public SeasonEntity() {
    }

    /**
     * Создаёт новый сезон в статусе {@link SeasonStatus#CREATED}.
     *
     * @param seasonNumber порядковый номер сезона
     * @param name название сезона
     * @param plannedEndDate планируемая дата завершения
     */
    public SeasonEntity(
            Integer seasonNumber,
            String name,
            LocalDateTime plannedEndDate
    ) {
        this.seasonNumber = seasonNumber;
        this.name = name;
        this.plannedEndDate = plannedEndDate;
        this.status = SeasonStatus.CREATED;
    }

    public Long getId() {
        return id;
    }

    public Integer getSeasonNumber() {
        return seasonNumber;
    }

    public void setSeasonNumber(Integer seasonNumber) {
        this.seasonNumber = seasonNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SeasonStatus getStatus() {
        return status;
    }

    public void setStatus(SeasonStatus status) {
        this.status = status;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getPlannedEndDate() {
        return plannedEndDate;
    }

    public void setPlannedEndDate(LocalDateTime plannedEndDate) {
        this.plannedEndDate = plannedEndDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }
}