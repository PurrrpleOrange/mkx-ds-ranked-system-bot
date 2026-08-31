package com.mkx.ranked.repository;

import com.mkx.ranked.model.MatchEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRepository extends JpaRepository<MatchEntity, Long> {

    List<MatchEntity> findAllBySeasonOrderByCreatedAtDesc(
            SeasonEntity season
    );

    List<MatchEntity> findAllByWinnerOrLoserOrderByCreatedAtDesc(
            SeasonPlayerEntity winner,
            SeasonPlayerEntity loser
    );

    List<MatchEntity> findAllBySeasonAndWinnerOrSeasonAndLoserOrderByCreatedAtDesc(
            SeasonEntity season1,
            SeasonPlayerEntity winner,
            SeasonEntity season2,
            SeasonPlayerEntity loser
    );
}