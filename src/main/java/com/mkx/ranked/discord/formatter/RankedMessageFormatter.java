package com.mkx.ranked.discord.formatter;

import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.MatchHistoryEntryDto;
import com.mkx.ranked.model.dto.MatchReportPreviewDto;
import com.mkx.ranked.model.dto.MatchResult;
import com.mkx.ranked.model.dto.PlayerProfileDto;
import com.mkx.ranked.model.dto.RegistrationResultDto;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RankedMessageFormatter {

    private static final Color INFORMATION_COLOR = new Color(0, 255, 200);
    private static final int DISCORD_MESSAGE_CHUNK_SIZE = 1900;
    private static final DateTimeFormatter TABLE_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public MessageEmbed rankedMenu(PlayerProfileDto profile) {
        EmbedBuilder embed = new EmbedBuilder();
        String rank = profile.rank() == null ? "Без ранга" : "#" + profile.rank();
        embed.setTitle("Mortal Kombat X - Ranked Season #" + profile.season().seasonNumber());
        embed.setColor(INFORMATION_COLOR);
        embed.setDescription("""
                Привет, **%s**!

                **Твой MMR:** `%d`
                **Место в топе:** **%s**
                **Сыграно игр:** %d
                **Дивизион:** %s %s

                **Сезон:** %s
                **Окончание:** %s
                """.formatted(
                profile.displayName(),
                profile.rating(),
                rank,
                profile.gamesPlayed(),
                profile.tierEmoji(),
                profile.tierName(),
                profile.season().name(),
                formatDiscordTimestamp(profile.season().plannedEndDate())
        ));
        return embed.build();
    }

    public MessageEmbed registrationCompleted(RegistrationResultDto result) {
        return new EmbedBuilder()
                .setTitle("Регистрация завершена")
                .setColor(INFORMATION_COLOR)
                .setDescription("""
                        Игрок **%s** зарегистрирован в текущем сезоне.

                        **Сезон:** #%d
                        **Рейтинг:** %d MMR
                        **Сыграно игр:** %d

                        Напишите `/ranked`, чтобы открыть меню.
                        """.formatted(
                        result.displayName(),
                        result.seasonNumber(),
                        result.rating(),
                        result.gamesPlayed()
                ))
                .build();
    }

    public MessageEmbed matchReportConfirmation(MatchReportPreviewDto preview) {
        return new EmbedBuilder()
                .setTitle("Подтверждение результата матча FT5")
                .setColor(INFORMATION_COLOR)
                .setDescription("""
                        **%s** заявляет о завершении матча.

                        **Заявленный счет:**
                        - **%s:** %d
                        - **%s:** %d

                        <@%d>, подтвердите или отклоните этот результат.
                        """.formatted(
                        preview.reporterDisplayName(),
                        preview.reporterDisplayName(),
                        preview.reporterScore(),
                        preview.opponentDisplayName(),
                        preview.opponentScore(),
                        preview.opponentDiscordId()
                ))
                .setFooter("Матч будет учтен только после подтверждения соперником.")
                .build();
    }

    public MessageEmbed matchRegistered(MatchResult result, long confirmedByDiscordId) {
        return new EmbedBuilder()
                .setTitle("Матч зарегистрирован")
                .setColor(Color.GREEN)
                .setDescription("""
                        Результат подтвержден <@%d>.

                        **Итоговый счет:** FT5 (%d : %d)

                        **Победитель:** <@%d> (`+%d MMR`, новый рейтинг: **%d**)
                        **Проигравший:** <@%d> (`%d MMR`, новый рейтинг: **%d**)
                        """.formatted(
                        confirmedByDiscordId,
                        result.winnerScore(),
                        result.loserScore(),
                        result.winnerDiscordId(),
                        result.deltaWinner(),
                        result.newWinnerRating(),
                        result.loserDiscordId(),
                        result.deltaLoser(),
                        result.newLoserRating()
                ))
                .setFooter("Match #" + result.matchId())
                .build();
    }

    public MessageEmbed matchRejected(long reporterDiscordId, long opponentDiscordId, long rejectedByDiscordId) {
        return new EmbedBuilder()
                .setTitle("Запись матча отменена")
                .setColor(Color.RED)
                .setDescription("""
                        Заявленный результат матча между <@%d> и <@%d> был отклонен <@%d>.

                        Рейтинг игроков не изменился.
                        """.formatted(reporterDiscordId, opponentDiscordId, rejectedByDiscordId))
                .build();
    }

    public List<String> matchHistory(List<MatchHistoryEntryDto> matches) {
        List<DiscordTableFormatter.Column> columns = List.of(
                DiscordTableFormatter.Column.right("ID"),
                DiscordTableFormatter.Column.left("ИТОГ"),
                DiscordTableFormatter.Column.left("СОПЕРНИК"),
                DiscordTableFormatter.Column.right("СЧЁТ"),
                DiscordTableFormatter.Column.right("MMR"),
                DiscordTableFormatter.Column.left("ДАТА")
        );
        List<List<String>> rows = matches.stream()
                .map(match -> List.of(
                        String.valueOf(match.matchId()),
                        match.win() ? "WIN" : "LOSE",
                        match.opponentDisplayName(),
                        match.scoreFor() + ":" + match.scoreAgainst(),
                        "%+d".formatted(match.ratingDelta()),
                        formatTableDateTime(match.createdAt())
                ))
                .toList();
        return DiscordTableFormatter.render(
                "ИСТОРИЯ МАТЧЕЙ ТЕКУЩЕГО СЕЗОНА",
                null,
                columns,
                rows,
                DISCORD_MESSAGE_CHUNK_SIZE
        );
    }

    public List<String> leaderboard(List<LeaderboardEntryDto> players) {
        List<DiscordTableFormatter.Column> columns = List.of(
                DiscordTableFormatter.Column.right("#"),
                DiscordTableFormatter.Column.left("НИК"),
                DiscordTableFormatter.Column.left("DISCORD"),
                DiscordTableFormatter.Column.right("MMR"),
                DiscordTableFormatter.Column.right("ИГРЫ")
        );

        Map<Division, List<List<String>>> rowsByDivision = new LinkedHashMap<>();
        for (LeaderboardEntryDto player : players) {
            Division division = new Division(player.tierName(), player.tierEmoji());
            rowsByDivision.computeIfAbsent(division, ignored -> new ArrayList<>())
                    .add(List.of(
                        String.valueOf(player.rank()),
                        player.displayName(),
                        "@" + player.discordUsername(),
                        String.valueOf(player.rating()),
                        String.valueOf(player.gamesPlayed())
                    ));
        }
        List<DiscordTableFormatter.Group> groups = rowsByDivision.entrySet().stream()
                .map(entry -> new DiscordTableFormatter.Group(
                        entry.getKey().label(),
                        entry.getValue()
                ))
                .toList();

        return DiscordTableFormatter.renderGrouped(
                "ТАБЛИЦА ЛИДЕРОВ ТЕКУЩЕГО СЕЗОНА",
                null,
                columns,
                groups,
                DISCORD_MESSAGE_CHUNK_SIZE
        );
    }

    private record Division(String name, String emoji) {

        private String label() {
            return (emoji == null || emoji.isBlank() ? "" : emoji + " ") + name;
        }
    }

    private String formatTableDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "—" : dateTime.format(TABLE_DATE_TIME_FORMAT);
    }

    private String formatDiscordTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "не указано";
        }

        long epochSecond = dateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        return "<t:" + epochSecond + ":F> (<t:" + epochSecond + ":R>)";
    }
}
