package com.mkx.ranked.service;

import com.mkx.ranked.exception.PlayerNotFoundException;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.AdminPlayerDto;
import com.mkx.ranked.model.dto.AdminRegisteredPlayerDto;
import com.mkx.ranked.repository.PlayerRepository;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerServiceTest {

    @Test
    void adminPlayerLookupRejectsUnknownDiscordUser() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        PlayerService service = new PlayerService(
                playerRepository,
                mock(SeasonPlayerRepository.class),
                mock(SeasonService.class)
        );
        when(playerRepository.findByDiscordId(999L)).thenReturn(Optional.empty());

        assertThrows(PlayerNotFoundException.class, () -> service.getAdminPlayerInfo(999L));
    }

    @Test
    void adminPlayerInfoUsesActiveSeasonProfileAndLeaderboardRank() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        SeasonPlayerRepository seasonPlayerRepository = mock(SeasonPlayerRepository.class);
        SeasonService seasonService = mock(SeasonService.class);
        PlayerService service = new PlayerService(playerRepository, seasonPlayerRepository, seasonService);

        PlayerEntity player = new PlayerEntity(123L, "discord-name");
        ReflectionTestUtils.setField(player, "id", 10L);
        SeasonEntity season = new SeasonEntity(9, "Season 9", null);
        ReflectionTestUtils.setField(season, "id", 90L);
        SeasonPlayerEntity leader = seasonPlayer(1L, new PlayerEntity(999L, "leader"), season, "Leader", 1400);
        SeasonPlayerEntity participant = seasonPlayer(2L, player, season, "Sub-Zero", 1250);
        participant.setGamesPlayed(12);

        when(playerRepository.findByDiscordId(123L)).thenReturn(Optional.of(player));
        when(seasonService.getActiveSeasonEntity()).thenReturn(season);
        when(seasonPlayerRepository.findBySeasonAndPlayer(season, player)).thenReturn(Optional.of(participant));
        when(seasonPlayerRepository.findLeaderboardBySeason(season)).thenReturn(List.of(leader, participant));

        AdminPlayerDto result = service.getAdminPlayerInfo(123L);

        assertEquals(10L, result.playerId());
        assertEquals(123L, result.discordId());
        assertEquals("discord-name", result.discordUsername());
        assertEquals("Sub-Zero", result.displayName());
        assertEquals(1250, result.rating());
        assertEquals(12, result.gamesPlayed());
        assertEquals(2, result.rank());
        assertEquals(9, result.seasonNumber());
    }

    @Test
    void registeredPlayerListIncludesParticipantsWithoutGamesInRepositoryOrder() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        SeasonPlayerRepository seasonPlayerRepository = mock(SeasonPlayerRepository.class);
        SeasonService seasonService = mock(SeasonService.class);
        PlayerService service = new PlayerService(playerRepository, seasonPlayerRepository, seasonService);
        SeasonEntity season = new SeasonEntity(9, "Season 9", null);
        PlayerEntity firstPlayer = new PlayerEntity(11L, "first-discord");
        PlayerEntity secondPlayer = new PlayerEntity(22L, "second-discord");
        SeasonPlayerEntity first = seasonPlayer(1L, firstPlayer, season, "First", 1000);
        SeasonPlayerEntity second = seasonPlayer(2L, secondPlayer, season, "Second", 1200);
        second.setGamesPlayed(5);

        when(seasonService.getActiveSeasonEntity()).thenReturn(season);
        when(seasonPlayerRepository.findAllRegisteredBySeason(season)).thenReturn(List.of(first, second));

        List<AdminRegisteredPlayerDto> result = service.getAllRegisteredPlayersForActiveSeason();

        assertEquals(List.of("First", "Second"), result.stream()
                .map(AdminRegisteredPlayerDto::displayName)
                .toList());
        assertEquals(List.of(0, 5), result.stream()
                .map(AdminRegisteredPlayerDto::gamesPlayed)
                .toList());
    }

    private SeasonPlayerEntity seasonPlayer(
            long id,
            PlayerEntity player,
            SeasonEntity season,
            String displayName,
            int rating
    ) {
        if (player.getId() == null) {
            ReflectionTestUtils.setField(player, "id", id + 100L);
        }
        SeasonPlayerEntity seasonPlayer = new SeasonPlayerEntity(player, season, displayName);
        ReflectionTestUtils.setField(seasonPlayer, "id", id);
        seasonPlayer.setRating(rating);
        return seasonPlayer;
    }
}
