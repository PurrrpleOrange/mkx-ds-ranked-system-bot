package com.mkx.ranked.repository;

import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
            select sp
            from SeasonPlayerEntity sp
            join fetch sp.player
            where sp.season = :season
            order by sp.rating desc, sp.gamesPlayed desc, sp.player.id asc
            """)
    List<SeasonPlayerEntity> findLeaderboardBySeason(@Param("season") SeasonEntity season);

    @Query(
            value = """
                    select sp
                    from SeasonPlayerEntity sp
                    join fetch sp.player
                    where sp.season = :season
                    order by sp.rating desc, sp.gamesPlayed desc, sp.player.id asc
                    """,
            countQuery = """
                    select count(sp)
                    from SeasonPlayerEntity sp
                    where sp.season = :season
                    """
    )
    Page<SeasonPlayerEntity> findLeaderboardBySeason(
            @Param("season") SeasonEntity season,
            Pageable pageable
    );

    Page<SeasonPlayerEntity> findAllBySeason(
            SeasonEntity season,
            Pageable pageable
    );

    List<SeasonPlayerEntity> findAllByPlayerOrderBySeason_SeasonNumberDesc(
            PlayerEntity player
    );

    long countBySeasonAndRatingGreaterThan(
            SeasonEntity season,
            Integer rating
    );
}
