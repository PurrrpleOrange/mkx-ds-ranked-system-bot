package com.mkx.ranked.service;

import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.exception.SeasonNotActiveException;
import com.mkx.ranked.exception.SeasonNotFoundException;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.SeasonDto;
import com.mkx.ranked.model.enums.SeasonStatus;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import com.mkx.ranked.repository.SeasonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SeasonService {

    private static final Logger log = LoggerFactory.getLogger(SeasonService.class);

    private final SeasonRepository seasonRepository;
    private final SeasonPlayerRepository seasonPlayerRepository;

    public SeasonService(
            SeasonRepository seasonRepository,
            SeasonPlayerRepository seasonPlayerRepository
    ) {
        this.seasonRepository = seasonRepository;
        this.seasonPlayerRepository = seasonPlayerRepository;
    }

    @Transactional(readOnly = true)
    public SeasonEntity getActiveSeasonEntity() {
        return seasonRepository.findByStatus(SeasonStatus.ACTIVE)
                .orElseThrow(SeasonNotActiveException::new);
    }

    @Transactional(readOnly = true)
    public SeasonEntity getCurrentSeasonEntity() {
        return getActiveSeasonEntity();
    }

    @Transactional(readOnly = true)
    public SeasonDto getCurrentSeason() {
        return getActiveSeason();
    }

    @Transactional(readOnly = true)
    public SeasonDto getActiveSeason() {
        return toDto(getActiveSeasonEntity());
    }

    @Transactional(readOnly = true)
    public SeasonDto getSeasonById(long seasonId) {
        return toDto(getSeasonEntityById(seasonId));
    }

    @Transactional(readOnly = true)
    public SeasonEntity getSeasonEntityById(long seasonId) {
        return seasonRepository.findById(seasonId)
                .orElseThrow(() -> new SeasonNotFoundException(seasonId));
    }

    @Transactional(readOnly = true)
    public SeasonDto getSeasonByNumber(Integer seasonNumber) {
        return findSeason(seasonNumber);
    }

    @Transactional
    public SeasonDto createNewSeason(String name, LocalDateTime plannedEndDate) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("Season name must not be blank.");
        }

        int nextSeasonNumber = seasonRepository.findMaxSeasonNumber().orElse(0) + 1;

        SeasonEntity season = new SeasonEntity(nextSeasonNumber, name.trim(), plannedEndDate);
        SeasonEntity saved = seasonRepository.save(season);
        log.info("SEASON SUCCESS: created season #{} ({})", saved.getSeasonNumber(), saved.getName());
        return toDto(saved);
    }

    @Transactional
    public SeasonDto activateSeason(long seasonId) {
        SeasonEntity season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new SeasonNotFoundException(seasonId));
        return activateSeason(season);
    }

    @Transactional
    public SeasonDto activateSeasonByNumber(Integer seasonNumber) {
        SeasonEntity season = seasonRepository.findBySeasonNumber(seasonNumber)
                .orElseThrow(() -> new SeasonNotFoundException(seasonNumber));
        return activateSeason(season);
    }

    @Transactional
    public boolean updatePlannedEndDate(LocalDateTime newPlannedEndDate) {
        SeasonEntity currentSeason = getCurrentSeasonEntity();
        currentSeason.setPlannedEndDate(newPlannedEndDate);
        seasonRepository.save(currentSeason);
        log.info("SEASON SUCCESS: updated planned end date for season #{}", currentSeason.getSeasonNumber());
        return true;
    }

    @Transactional
    public SeasonDto finishActiveSeason() {
        SeasonEntity currentSeason = getActiveSeasonEntity();
        return finishSeason(currentSeason);
    }

    @Transactional
    public boolean endCurrentSeason() {
        finishActiveSeason();
        return true;
    }

    @Transactional
    public SeasonDto finishSeason(long seasonId) {
        SeasonEntity season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new SeasonNotFoundException(seasonId));
        return finishSeason(season);
    }

    @Transactional
    public SeasonDto finishSeasonByNumber(Integer seasonNumber) {
        SeasonEntity season = seasonRepository.findBySeasonNumber(seasonNumber)
                .orElseThrow(() -> new SeasonNotFoundException(seasonNumber));
        return finishSeason(season);
    }

    @Transactional(readOnly = true)
    public SeasonDto findSeason(Integer seasonNumber) {
        return seasonRepository.findBySeasonNumber(seasonNumber)
                .map(this::toDto)
                .orElseThrow(() -> new SeasonNotFoundException(seasonNumber));
    }

    SeasonDto toDto(SeasonEntity season) {
        return new SeasonDto(
                season.getId(),
                season.getSeasonNumber(),
                season.getName(),
                season.getStatus(),
                season.getPlannedEndDate()
        );
    }

    private SeasonDto activateSeason(SeasonEntity season) {
        if (season.getStatus() != SeasonStatus.CREATED) {
            throw new BusinessException("Only CREATED seasons can be activated.");
        }

        if (seasonRepository.countByStatus(SeasonStatus.ACTIVE) > 0) {
            throw new BusinessException("There is already an active ranked season.");
        }

        season.setStatus(SeasonStatus.ACTIVE);
        season.setStartDate(LocalDateTime.now());
        SeasonEntity saved = seasonRepository.save(season);
        log.info("SEASON SUCCESS: activated season #{}", saved.getSeasonNumber());
        return toDto(saved);
    }

    private SeasonDto finishSeason(SeasonEntity season) {
        if (season.getStatus() != SeasonStatus.ACTIVE) {
            throw new BusinessException("Only ACTIVE seasons can be finished.");
        }

        List<SeasonPlayerEntity> standings = seasonPlayerRepository.findLeaderboardBySeason(season);
        for (int i = 0; i < standings.size(); i++) {
            standings.get(i).setFinalRank(i + 1);
        }

        season.setStatus(SeasonStatus.FINISHED);
        season.setEndDate(LocalDateTime.now());
        seasonPlayerRepository.saveAll(standings);
        SeasonEntity saved = seasonRepository.save(season);

        log.info("SEASON SUCCESS: finished season #{}", saved.getSeasonNumber());
        return toDto(saved);
    }
}
