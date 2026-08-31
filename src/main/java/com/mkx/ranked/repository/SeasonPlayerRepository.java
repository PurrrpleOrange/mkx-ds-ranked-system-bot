package com.mkx.ranked.repository;

import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeasonPlayerRepository
        extends JpaRepository<SeasonPlayerEntity, Long> {

    Optional<SeasonPlayerEntity> findBySeasonAndPlayer(
            SeasonEntity season,
            PlayerEntity player
    );

    boolean existsBySeasonAndPlayer(
            SeasonEntity season,
            PlayerEntity player
    );

    List<SeasonPlayerEntity> findAllBySeasonOrderByRatingDesc(
            SeasonEntity season
    );

    List<SeasonPlayerEntity> findAllByPlayerOrderBySeasonNumberDesc(
            PlayerEntity player
    );
}