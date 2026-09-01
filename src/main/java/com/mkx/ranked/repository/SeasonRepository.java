package com.mkx.ranked.repository;

import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.enums.SeasonStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SeasonRepository extends JpaRepository<SeasonEntity, Long> {

    Optional<SeasonEntity> findByStatus(SeasonStatus status);

    Optional<SeasonEntity> findBySeasonNumber(Integer seasonNumber);

    Optional<SeasonEntity> findFirstByStatusOrderBySeasonNumberDesc(SeasonStatus status);

    @Query("select max(s.seasonNumber) from SeasonEntity s")
    Optional<Integer> findMaxSeasonNumber();
}
