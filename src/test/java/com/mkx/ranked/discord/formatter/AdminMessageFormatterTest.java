package com.mkx.ranked.discord.formatter;

import com.mkx.ranked.model.dto.AdminMatchDto;
import com.mkx.ranked.model.dto.AdminPlayerDto;
import com.mkx.ranked.model.dto.AdminRegisteredPlayerDto;
import com.mkx.ranked.model.dto.AdminSeasonStatisticsDto;
import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.SeasonDto;
import com.mkx.ranked.model.enums.SeasonStatus;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminMessageFormatterTest {

    private final AdminMessageFormatter formatter = new AdminMessageFormatter();

    @Test
    void allInformationalAdminEmbedsUseTurquoise() {
        Color informationColor = new Color(0, 255, 200);
        SeasonDto season = season(90L, 9, SeasonStatus.ACTIVE);
        AdminMatchDto match = new AdminMatchDto(
                501L,
                9,
                "Sub-Zero",
                "Scorpion",
                11L,
                22L,
                5,
                2,
                18,
                -18,
                LocalDateTime.of(2026, 9, 1, 10, 0)
        );
        AdminPlayerDto player = new AdminPlayerDto(
                1L, 11L, "discord", "Sub-Zero", 1200, 5, 1, "Elder God", "🏆", 9
        );
        AdminSeasonStatisticsDto statistics = new AdminSeasonStatisticsDto(
                season,
                1,
                5,
                1200,
                List.of(new LeaderboardEntryDto(
                        1, 1L, 11L, "subzero.discord", "Sub-Zero", 1200, 5, "Elder God", "🏆"
                ))
        );

        assertEquals(informationColor, formatter.adminMenu().getColor());
        assertEquals(informationColor, formatter.seasonManagementMenu().getColor());
        assertEquals(informationColor, formatter.matchManagementMenu().getColor());
        assertEquals(informationColor, formatter.playerManagementMenu().getColor());
        assertEquals(informationColor, formatter.seasonInfo(season).getColor());
        assertEquals(informationColor, formatter.matchInfo(match).getColor());
        assertEquals(informationColor, formatter.playerInfo(player).getColor());
        assertEquals(informationColor, formatter.seasonList(List.of(season)).get(0).getColor());
        assertEquals(informationColor, formatter.seasonActivatedAnnouncement(season).getColor());
        assertEquals(informationColor, formatter.seasonFinishedAnnouncement(season).getColor());
        assertEquals(informationColor, formatter.previousSeasonStatistics(statistics).getColor());
    }

    @Test
    void seasonListContainsIdsNumbersStatusesAndNames() {
        List<MessageEmbed> embeds = formatter.seasonList(List.of(
                season(90L, 9, SeasonStatus.ACTIVE),
                season(80L, 8, SeasonStatus.FINISHED)
        ));

        String rendered = embeds.stream()
                .map(MessageEmbed::getDescription)
                .reduce("", String::concat);

        assertTrue(rendered.contains("ID  №  СТАТУС"));
        assertTrue(rendered.contains("90"));
        assertTrue(rendered.contains("9"));
        assertTrue(rendered.contains("ACTIVE"));
        assertTrue(rendered.contains("Season 9"));
        assertTrue(rendered.contains("80"));
        assertTrue(rendered.contains("FINISHED"));
        assertTrue(rendered.contains("```text"));
    }

    @Test
    void lifecycleAnnouncementsExplainRegistrationAndSeasonCompletion() {
        SeasonDto season = season(90L, 9, SeasonStatus.ACTIVE);

        String activated = formatter.seasonActivatedAnnouncement(season).getDescription();
        String finished = formatter.seasonFinishedAnnouncement(season).getDescription();

        assertTrue(activated.contains("/ranked"));
        assertTrue(activated.contains("Регистрация выполняется заново"));
        assertTrue(finished.contains("Итоговые места"));
        assertTrue(finished.contains("завершён"));
    }

    @Test
    void publishedLeaderboardUsesNumberedListAndSeasonStatisticsUsesAlignedTable() {
        LeaderboardEntryDto first = new LeaderboardEntryDto(
                1, 1L, 11L, "subzero.discord", "Sub-Zero", 1400, 10, "S-Tier", "🥇"
        );
        LeaderboardEntryDto second = new LeaderboardEntryDto(
                2, 2L, 22L, "scorpion.discord", "Scorpion", 1300, 8, "S-Tier", "🥇"
        );
        List<String> leaderboard = formatter.fullLeaderboard(4, List.of(first, second));
        AdminSeasonStatisticsDto statistics = new AdminSeasonStatisticsDto(
                season(80L, 8, SeasonStatus.FINISHED),
                1,
                10,
                1400,
                List.of(first)
        );

        String published = String.join("", leaderboard);
        String statisticsDescription = formatter.previousSeasonStatistics(statistics).getDescription();

        assertEquals(1, leaderboard.size());
        assertTrue(published.startsWith("**Актуальный рейтинг** 4 сезона"));
        assertTrue(published.contains("1. *Sub-Zero* - 1400 (10 игр)"));
        assertTrue(published.contains("2. *Scorpion* - 1300 (8 игр)"));
        assertTrue(statisticsDescription.contains("**Топ-10 сезона**"));
        assertTrue(statisticsDescription.contains("#  НИК"));
        assertTrue(statisticsDescription.contains("```text"));
    }

    @Test
    void managementMenusContainShortGuidesForTheirActions() {
        String root = formatter.adminMenu().getDescription();
        String seasons = formatter.seasonManagementMenu().getDescription();
        String matches = formatter.matchManagementMenu().getDescription();
        String players = formatter.playerManagementMenu().getDescription();

        assertTrue(root.contains("Опубликовать рейтинг"));
        assertTrue(root.contains("Управление сезонами"));
        assertTrue(root.contains("Управление матчами"));
        assertTrue(root.contains("Управление игроками"));
        assertTrue(seasons.contains("Список сезонов"));
        assertTrue(seasons.contains("Изменить информацию о сезоне"));
        assertTrue(matches.contains("Удалить матч"));
        assertTrue(players.contains("Посмотреть статистику игрока"));
        assertTrue(players.contains("Вывести всех зарегистрированных игроков"));
    }

    @Test
    void registeredPlayerListContainsPlayersWithoutGames() {
        List<String> messages = formatter.registeredPlayers(List.of(
                new AdminRegisteredPlayerDto(1L, 11L, "discord-one", "Noob", 1000, 0),
                new AdminRegisteredPlayerDto(22L, 22L, "d2", "Veteran", 1250, 8)
        ));

        String rendered = String.join("", messages);
        assertTrue(rendered.contains("Всего: **2**"));
        assertTrue(rendered.contains("ID  НИК      DISCORD        MMR  ИГРЫ"));
        assertTrue(rendered.contains(" 1  Noob     @discord-one  1000     0"));
        assertTrue(rendered.contains("22  Veteran  @d2           1250     8"));
        assertTrue(rendered.contains("```text"));
        assertTrue(rendered.endsWith("```"));
    }

    @Test
    void registeredPlayerTableDoesNotTruncateValuesAndKeepsCodeBlocksWhenSplit() {
        String longestName = "VeryLongPlayerNameThatMustRemainComplete";
        List<AdminRegisteredPlayerDto> players = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            players.add(new AdminRegisteredPlayerDto(
                    i,
                    10_000L + i,
                    "discord-user-" + i,
                    i == 100 ? longestName : "Player-" + i,
                    1000 + i,
                    i
            ));
        }

        List<String> messages = formatter.registeredPlayers(players);

        assertTrue(messages.size() > 1);
        assertTrue(messages.stream().allMatch(message -> message.contains("```text\n")));
        assertTrue(messages.stream().allMatch(message -> message.endsWith("```")));
        assertTrue(String.join("", messages).contains(longestName));
    }

    private SeasonDto season(long id, int number, SeasonStatus status) {
        return new SeasonDto(id, number, "Season " + number, status, null, null, null);
    }
}
