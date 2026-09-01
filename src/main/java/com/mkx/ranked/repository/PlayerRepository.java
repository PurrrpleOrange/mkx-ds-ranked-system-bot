package com.mkx.ranked.repository;

import com.mkx.ranked.model.PlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerRepository extends JpaRepository<PlayerEntity, Long> {

    Optional<PlayerEntity> findByDiscordId(Long discordId);

    Optional<PlayerEntity> findByUsername(String username);

    boolean existsByDiscordId(Long discordId);

    boolean existsByDisplayNameIgnoreCaseAndDiscordIdIsNotNull(String displayName);

    Optional<PlayerEntity> findFirstByDisplayNameIgnoreCaseAndDiscordIdIsNull(String displayName);
}
