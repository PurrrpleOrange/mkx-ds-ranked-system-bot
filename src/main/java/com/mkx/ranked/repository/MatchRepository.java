package com.mkx.ranked.repository;

import com.mkx.ranked.model.MatchEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MatchRepository extends JpaRepository<MatchEntity, Long> {

    long countBySeason(SeasonEntity season);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m
            from MatchEntity m
            where m.id = :matchId
            """)
    java.util.Optional<MatchEntity> findByIdForUpdate(@Param("matchId") Long matchId);

    List<MatchEntity> findAllBySeasonOrderByCreatedAtDesc(
            SeasonEntity season
    );

    List<MatchEntity> findAllByWinnerOrLoserOrderByCreatedAtDesc(
            SeasonPlayerEntity winner,
            SeasonPlayerEntity loser
    );

    @Query(
            value = """
                    select m
                    from MatchEntity m
                    where m.season = :season
                      and (m.winner = :participant or m.loser = :participant)
                    order by m.createdAt desc
                    """,
            countQuery = """
                    select count(m)
                    from MatchEntity m
                    where m.season = :season
                      and (m.winner = :participant or m.loser = :participant)
                    """
    )
    Page<MatchEntity> findBySeasonAndParticipant(
            @Param("season") SeasonEntity season,
            @Param("participant") SeasonPlayerEntity participant,
            Pageable pageable
    );
}
