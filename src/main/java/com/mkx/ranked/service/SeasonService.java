package com.mkx.ranked.service;

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
    public SeasonEntity getCurrentSeasonEntity() {
        return seasonRepository.findFirstByStatusOrderBySeasonNumberDesc(SeasonStatus.ACTIVE)
                .orElseThrow(SeasonNotActiveException::new);
    }

    @Transactional(readOnly = true)
    public SeasonDto getCurrentSeason() {
        return toDto(getCurrentSeasonEntity());
    }

    @Transactional
    public SeasonDto createNewSeason(String name, LocalDateTime plannedEndDate) {
        int nextSeasonNumber = seasonRepository.findMaxSeasonNumber().orElse(0) + 1;

        SeasonEntity season = new SeasonEntity(nextSeasonNumber, name, plannedEndDate);
        season.setStatus(SeasonStatus.ACTIVE);
        season.setStartDate(LocalDateTime.now());

        SeasonEntity saved = seasonRepository.save(season);
        log.info("SEASON SUCCESS: created active season #{} ({})", saved.getSeasonNumber(), saved.getName());
        return toDto(saved);
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
    public boolean endCurrentSeason() {
        SeasonEntity currentSeason = getCurrentSeasonEntity();
        List<SeasonPlayerEntity> standings =
                seasonPlayerRepository.findAllBySeasonOrderByRatingDesc(currentSeason);

        for (int i = 0; i < standings.size(); i++) {
            standings.get(i).setFinalRank(i + 1);
        }

        currentSeason.setStatus(SeasonStatus.FINISHED);
        currentSeason.setEndDate(LocalDateTime.now());
        seasonPlayerRepository.saveAll(standings);
        seasonRepository.save(currentSeason);

        log.info("SEASON SUCCESS: finished season #{}", currentSeason.getSeasonNumber());
        return true;
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
}
