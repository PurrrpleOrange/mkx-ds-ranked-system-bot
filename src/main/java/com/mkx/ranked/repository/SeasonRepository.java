package com.mkx.ranked.repository;

import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.enums.SeasonStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeasonRepository extends JpaRepository<SeasonEntity, Long> {

    Optional<SeasonEntity> findByStatus(SeasonStatus status);

    Optional<SeasonEntity> findBySeasonNumber(Integer seasonNumber);
}