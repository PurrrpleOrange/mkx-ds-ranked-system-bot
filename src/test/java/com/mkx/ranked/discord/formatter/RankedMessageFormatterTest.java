package com.mkx.ranked.discord.formatter;

import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.MatchHistoryEntryDto;
import com.mkx.ranked.model.dto.MatchReportPreviewDto;
import com.mkx.ranked.model.dto.MatchResult;
import com.mkx.ranked.model.dto.PlayerProfileDto;
import com.mkx.ranked.model.dto.RegistrationResultDto;
import com.mkx.ranked.model.dto.SeasonDto;
import com.mkx.ranked.model.enums.SeasonStatus;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankedMessageFormatterTest {

    private final RankedMessageFormatter formatter = new RankedMessageFormatter();

    @Test
    void usesTurquoiseForInformationGreenForConfirmedAndRedForRejected() {
        Color informationColor = new Color(0, 255, 200);
        SeasonDto season = new SeasonDto(90L, 9, "Season 9", SeasonStatus.ACTIVE, null, null, null);
        PlayerProfileDto profile = new PlayerProfileDto(
                1L, 11L, "Sub-Zero", 1200, 5, 1, "Elder God", "🏆", season
        );
        RegistrationResultDto registration = new RegistrationResultDto(
                1L, 11L, "discord", "Sub-Zero", 90L, 9, 1000, 0
        );
        MatchReportPreviewDto preview = new MatchReportPreviewDto(
                11L, 22L, "Sub-Zero", "Scorpion", 5, 2
        );
        MatchResult confirmed = new MatchResult(
                501L, 11L, "Sub-Zero", 22L, "Scorpion", 5, 2, 18, -18, 1218, 1182
        );

        assertEquals(informationColor, formatter.rankedMenu(profile).getColor());
        assertEquals(informationColor, formatter.registrationCompleted(registration).getColor());
        assertEquals(informationColor, formatter.matchReportConfirmation(preview).getColor());
        assertEquals(Color.GREEN, formatter.matchRegistered(confirmed, 22L).getColor());
        assertEquals(Color.RED, formatter.matchRejected(11L, 22L, 22L).getColor());
    }

    @Test
    void rankedMenuUsesCompactSeasonProfileLayout() {
        SeasonDto season = new SeasonDto(
                40L,
                4,
                "Moscow Kombat",
                SeasonStatus.ACTIVE,
                null,
                LocalDateTime.of(2026, 12, 1, 20, 0),
                null
        );
        PlayerProfileDto profile = new PlayerProfileDto(
                1L, 11L, "Sub-Zero", 999, 3, 10, "S-Tier", "🥇", season
        );

        var embed = formatter.rankedMenu(profile);
        String description = embed.getDescription();

        assertEquals("Season #4", embed.getTitle());
        assertTrue(description.startsWith("**Moscow Kombat**"));
        assertTrue(description.contains("Привет, **Sub-Zero**!"));
        assertTrue(description.contains("🥇 **S-Tier**"));
        assertTrue(description.contains(
                "**Твой MMR:** `999` • **Место в топе:** **#10** • **Сыграно игр:** 3"
        ));
        assertTrue(description.contains("*Дата окончания сезона:"));
    }

    @Test
    void fullMatchHistoryShowsEveryMatchIdWithoutPagination() {
        List<MatchHistoryEntryDto> matches = List.of(
                new MatchHistoryEntryDto(
                        502L,
                        true,
                        "Scorpion",
                        5,
                        2,
                        18,
                        LocalDateTime.of(2026, 9, 1, 10, 0)
                ),
                new MatchHistoryEntryDto(
                        501L,
                        false,
                        "Sub-Zero",
                        4,
                        5,
                        -20,
                        LocalDateTime.of(2026, 8, 31, 20, 0)
                )
        );

        String rendered = String.join("", formatter.matchHistory(matches));

        assertTrue(rendered.contains("ID  ИТОГ"));
        assertTrue(rendered.contains("502"));
        assertTrue(rendered.contains("501"));
        assertTrue(rendered.contains("WIN"));
        assertTrue(rendered.contains("LOSE"));
        assertTrue(rendered.contains("Scorpion"));
        assertTrue(rendered.contains("Sub-Zero"));
        assertTrue(rendered.contains("01.09.2026 10:00"));
        assertTrue(rendered.contains("```ansi"));
        assertTrue(rendered.contains("\u001B[1;37;42mWIN \u001B[0m"));
        assertTrue(rendered.contains("\u001B[1;37;41mLOSE\u001B[0m"));
        assertFalse(rendered.toLowerCase().contains("страница"));
    }

    @Test
    void fullLeaderboardShowsAllPlayersWithoutPagination() {
        List<LeaderboardEntryDto> players = List.of(
                new LeaderboardEntryDto(1, 1L, 11L, "first.discord", "First", 1400, 10, "S-Tier", "🥇"),
                new LeaderboardEntryDto(11, 2L, 22L, "second.discord", "Second", 1300, 8, "A-Tier", "🥈")
        );

        String rendered = String.join("", formatter.leaderboard(players));

        assertTrue(rendered.contains("#  НИК"));
        assertTrue(rendered.contains("First"));
        assertTrue(rendered.contains("Second"));
        assertTrue(rendered.contains("@first.discord"));
        assertTrue(rendered.contains("@second.discord"));
        assertTrue(rendered.contains("1400"));
        assertTrue(rendered.contains("🥇 S-Tier"));
        assertTrue(rendered.contains("🥈 A-Tier"));
        assertFalse(rendered.contains("ДИВИЗИОН"));
        assertEquals(1, occurrences(rendered, "#  НИК"));
        assertTrue(rendered.contains("```text"));
        assertFalse(rendered.toLowerCase().contains("страница"));
    }

    private int occurrences(String value, String fragment) {
        return (value.length() - value.replace(fragment, "").length()) / fragment.length();
    }
}
