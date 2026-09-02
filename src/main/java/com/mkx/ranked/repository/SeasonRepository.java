package com.mkx.ranked.repository;

import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.enums.SeasonStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeasonRepository extends JpaRepository<SeasonEntity, Long> {

    Optional<SeasonEntity> findByStatus(SeasonStatus status);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select s from SeasonEntity s where s.status = :status")
    Optional<SeasonEntity> findByStatusForReadLock(@Param("status") SeasonStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SeasonEntity s where s.status = :status")
    Optional<SeasonEntity> findByStatusForUpdate(@Param("status") SeasonStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SeasonEntity s where s.id = :seasonId")
    Optional<SeasonEntity> findByIdForUpdate(@Param("seasonId") Long seasonId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SeasonEntity s where s.seasonNumber = :seasonNumber")
    Optional<SeasonEntity> findBySeasonNumberForUpdate(@Param("seasonNumber") Integer seasonNumber);

    long countByStatus(SeasonStatus status);

    Optional<SeasonEntity> findBySeasonNumber(Integer seasonNumber);

    boolean existsBySeasonNumber(Integer seasonNumber);

    boolean existsBySeasonNumberAndIdNot(Integer seasonNumber, Long id);

    Optional<SeasonEntity> findFirstByStatusOrderBySeasonNumberDesc(SeasonStatus status);

    List<SeasonEntity> findAllByStatusOrderByEndDateDescSeasonNumberDesc(SeasonStatus status);

    List<SeasonEntity> findAllByOrderBySeasonNumberDesc();

    @Query(value = "select nextval('season_number_seq')", nativeQuery = true)
    Long getNextSeasonNumber();
}
