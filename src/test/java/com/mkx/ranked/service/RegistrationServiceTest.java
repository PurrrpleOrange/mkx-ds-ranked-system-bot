package com.mkx.ranked.service;

import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.exception.SeasonNotActiveException;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.RegistrationResultDto;
import com.mkx.ranked.repository.PlayerRepository;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationServiceTest {

    private PlayerRepository playerRepository;
    private SeasonPlayerRepository seasonPlayerRepository;
    private SeasonService seasonService;
    private RegistrationService service;
    private SeasonEntity activeSeason;

    @BeforeEach
    void setUp() {
        playerRepository = mock(PlayerRepository.class);
        seasonPlayerRepository = mock(SeasonPlayerRepository.class);
        seasonService = mock(SeasonService.class);
        service = new RegistrationService(playerRepository, seasonPlayerRepository, seasonService);

        activeSeason = mock(SeasonEntity.class);
        when(activeSeason.getId()).thenReturn(10L);
        when(activeSeason.getSeasonNumber()).thenReturn(2);
        when(seasonService.getActiveSeasonEntity()).thenReturn(activeSeason);
        when(seasonService.getActiveSeasonEntityForReadLock()).thenReturn(activeSeason);
    }

    @Test
    void newDiscordUserCreatesPlayerAndSeasonParticipation() {
        PlayerEntity savedPlayer = player(1L, 100L, "discord-user");
        when(playerRepository.findByDiscordId(100L)).thenReturn(Optional.empty());
        when(playerRepository.save(any(PlayerEntity.class))).thenReturn(savedPlayer);
        when(seasonPlayerRepository.saveAndFlush(any(SeasonPlayerEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationResultDto result = service.register(100L, "discord-user", "  Sub-Zero  ");

        ArgumentCaptor<PlayerEntity> playerCaptor = ArgumentCaptor.forClass(PlayerEntity.class);
        verify(playerRepository).save(playerCaptor.capture());
        assertEquals(100L, playerCaptor.getValue().getDiscordId());
        assertEquals("discord-user", playerCaptor.getValue().getUsername());

        ArgumentCaptor<SeasonPlayerEntity> participationCaptor =
                ArgumentCaptor.forClass(SeasonPlayerEntity.class);
        verify(seasonPlayerRepository).saveAndFlush(participationCaptor.capture());
        SeasonPlayerEntity participation = participationCaptor.getValue();
        assertEquals(activeSeason, participation.getSeason());
        assertEquals(savedPlayer, participation.getPlayer());
        assertEquals("Sub-Zero", participation.getDisplayName());
        assertEquals(1000, participation.getRating());
        assertEquals(0, participation.getGamesPlayed());
        assertNull(participation.getFinalRank());
        assertEquals("Sub-Zero", result.displayName());
    }

    @Test
    void existingPlayerInNewSeasonCreatesOnlySeasonParticipation() {
        PlayerEntity existingPlayer = player(1L, 100L, "old-discord-name");
        when(playerRepository.findByDiscordId(100L)).thenReturn(Optional.of(existingPlayer));
        when(playerRepository.save(existingPlayer)).thenReturn(existingPlayer);
        when(seasonPlayerRepository.existsBySeasonAndPlayer(activeSeason, existingPlayer)).thenReturn(false);
        when(seasonPlayerRepository.saveAndFlush(any(SeasonPlayerEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.register(100L, "new-discord-name", "ScorpionMain");

        verify(playerRepository).save(existingPlayer);
        assertEquals("new-discord-name", existingPlayer.getUsername());
        verify(seasonPlayerRepository).saveAndFlush(any(SeasonPlayerEntity.class));
    }

    @Test
    void samePlayerCannotRegisterTwiceInSameSeason() {
        PlayerEntity existingPlayer = player(1L, 100L, "discord-user");
        when(playerRepository.findByDiscordId(100L)).thenReturn(Optional.of(existingPlayer));
        when(seasonPlayerRepository.existsBySeasonAndPlayer(activeSeason, existingPlayer)).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.register(100L, "discord-user", "Sub-Zero")
        );

        assertEquals("You are already registered in the current season.", exception.getMessage());
        verify(playerRepository, never()).save(any(PlayerEntity.class));
        verify(seasonPlayerRepository, never()).saveAndFlush(any(SeasonPlayerEntity.class));
    }

    @Test
    void registrationRequiresActiveSeason() {
        when(seasonService.getActiveSeasonEntityForReadLock()).thenThrow(new SeasonNotActiveException());

        assertThrows(
                SeasonNotActiveException.class,
                () -> service.register(100L, "discord-user", "Sub-Zero")
        );

        verify(playerRepository, never()).findByDiscordId(any(Long.class));
        verify(playerRepository, never()).save(any(PlayerEntity.class));
    }

    @Test
    void sameGameUsernameIsRejectedCaseInsensitivelyWithinSeason() {
        when(playerRepository.findByDiscordId(200L)).thenReturn(Optional.empty());
        when(seasonPlayerRepository.existsBySeasonAndDisplayNameIgnoreCase(activeSeason, "scorpionmain"))
                .thenReturn(true);

        assertThrows(
                BusinessException.class,
                () -> service.register(200L, "another-discord-user", "scorpionmain")
        );

        verify(playerRepository, never()).save(any(PlayerEntity.class));
        verify(seasonPlayerRepository, never()).saveAndFlush(any(SeasonPlayerEntity.class));
    }

    @Test
    void sameDisplayNameInDifferentSeasonIsAllowed() {
        PlayerEntity existingPlayer = player(1L, 100L, "discord-user");
        when(playerRepository.findByDiscordId(100L)).thenReturn(Optional.of(existingPlayer));
        when(playerRepository.save(existingPlayer)).thenReturn(existingPlayer);
        when(seasonPlayerRepository.existsBySeasonAndPlayer(activeSeason, existingPlayer)).thenReturn(false);
        when(seasonPlayerRepository.existsBySeasonAndDisplayNameIgnoreCase(activeSeason, "Scorpion"))
                .thenReturn(false);
        when(seasonPlayerRepository.saveAndFlush(any(SeasonPlayerEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationResultDto result = service.register(100L, "discord-user", "Scorpion");

        assertEquals("Scorpion", result.displayName());
        verify(seasonPlayerRepository).saveAndFlush(any(SeasonPlayerEntity.class));
    }

    @Test
    void existingPlayerIsNotAutomaticallyRegisteredInNewSeason() {
        PlayerEntity existingPlayer = player(1L, 100L, "discord-user");
        when(playerRepository.findByDiscordId(100L)).thenReturn(Optional.of(existingPlayer));
        when(seasonPlayerRepository.existsBySeasonAndPlayer(activeSeason, existingPlayer)).thenReturn(false);

        assertFalse(service.isRegistered(100L));
    }

    @Test
    void concurrentDisplayNameConflictBecomesBusinessException() {
        PlayerEntity savedPlayer = player(2L, 200L, "discord-user");
        when(playerRepository.findByDiscordId(200L)).thenReturn(Optional.empty());
        when(playerRepository.save(any(PlayerEntity.class))).thenReturn(savedPlayer);
        when(seasonPlayerRepository.saveAndFlush(any(SeasonPlayerEntity.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key violates constraint uq_season_player_display_name_ci"
                ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.register(200L, "discord-user", "Scorpion")
        );

        assertEquals(
                "Username 'Scorpion' is already registered in the current season.",
                exception.getMessage()
        );
    }

    @Test
    void concurrentDiscordIdentityConflictBecomesAlreadyRegisteredBusinessException() {
        PlayerEntity savedPlayer = player(2L, 200L, "discord-user");
        when(playerRepository.findByDiscordId(200L)).thenReturn(Optional.empty());
        when(playerRepository.save(any(PlayerEntity.class))).thenReturn(savedPlayer);
        when(seasonPlayerRepository.saveAndFlush(any(SeasonPlayerEntity.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key violates constraint players_discord_id_key"
                ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.register(200L, "discord-user", "Scorpion")
        );

        assertEquals("You are already registered in the current season.", exception.getMessage());
    }

    @Test
    void unrelatedDataIntegrityViolationIsNotHidden() {
        PlayerEntity savedPlayer = player(2L, 200L, "discord-user");
        DataIntegrityViolationException databaseFailure =
                new DataIntegrityViolationException("some_other_constraint");
        when(playerRepository.findByDiscordId(200L)).thenReturn(Optional.empty());
        when(playerRepository.save(any(PlayerEntity.class))).thenReturn(savedPlayer);
        when(seasonPlayerRepository.saveAndFlush(any(SeasonPlayerEntity.class)))
                .thenThrow(databaseFailure);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> service.register(200L, "discord-user", "Scorpion")
        );

        assertEquals(databaseFailure, thrown);
    }

    private PlayerEntity player(long id, long discordId, String discordUsername) {
        PlayerEntity player = mock(PlayerEntity.class);
        AtomicReference<String> username = new AtomicReference<>(discordUsername);
        when(player.getId()).thenReturn(id);
        when(player.getDiscordId()).thenReturn(discordId);
        when(player.getUsername()).thenAnswer(ignored -> username.get());
        org.mockito.Mockito.doAnswer(invocation -> {
            username.set(invocation.getArgument(0));
            return null;
        }).when(player).setUsername(any(String.class));
        return player;
    }
}
