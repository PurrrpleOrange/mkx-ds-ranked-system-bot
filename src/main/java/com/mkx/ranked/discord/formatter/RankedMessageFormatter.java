package com.mkx.ranked.discord.formatter;

import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.MatchHistoryEntryDto;
import com.mkx.ranked.model.dto.MatchReportPreviewDto;
import com.mkx.ranked.model.dto.MatchResult;
import com.mkx.ranked.model.dto.PageDto;
import com.mkx.ranked.model.dto.PlayerProfileDto;
import com.mkx.ranked.model.dto.RegistrationProfileDto;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
public class RankedMessageFormatter {

    public MessageEmbed rankedMenu(PlayerProfileDto profile) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Mortal Kombat X - Ranked Season #" + profile.season().seasonNumber());
        embed.setColor(new Color(175, 0, 0));
        embed.setDescription("""
                Привет, **%s**!

                **Твой MMR:** `%d`
                **Место в топе:** **#%d**
                **Сыграно игр:** %d
                **Дивизион:** %s %s

                **Сезон:** %s
                **Окончание:** %s
                """.formatted(
                profile.displayName(),
                profile.rating(),
                profile.rank(),
                profile.gamesPlayed(),
                profile.tierEmoji(),
                profile.tierName(),
                profile.season().name(),
                formatDiscordTimestamp(profile.season().plannedEndDate())
        ));
        return embed.build();
    }

    public MessageEmbed registrationCandidate(RegistrationProfileDto profile) {
        return new EmbedBuilder()
                .setTitle("Найден импортированный профиль")
                .setColor(Color.CYAN)
                .setDescription("""
                        В базе найден профиль:

                        **Игровой ник:** %s
                        **Рейтинг:** %d MMR
                        **Сыграно игр:** %d

                        Это ваш профиль?
                        """.formatted(profile.displayName(), profile.rating(), profile.gamesPlayed()))
                .build();
    }

    public MessageEmbed newProfilePrompt(String nickname) {
        return new EmbedBuilder()
                .setTitle("Создание нового профиля")
                .setColor(Color.GREEN)
                .setDescription("""
                        Профиль с ником **%s** не найден.

                        Создать новый профиль со стартовым рейтингом **1000 MMR**?
                        """.formatted(nickname))
                .build();
    }

    public MessageEmbed matchReportConfirmation(MatchReportPreviewDto preview) {
        return new EmbedBuilder()
                .setTitle("Подтверждение результата матча FT5")
                .setColor(Color.ORANGE)
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

    public MessageEmbed matchHistory(PageDto<MatchHistoryEntryDto> page) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("История матчей (страница %d из %d)"
                .formatted(page.currentPage() + 1, page.totalPages()));
        embed.setColor(Color.CYAN);

        StringBuilder description = new StringBuilder();
        for (MatchHistoryEntryDto match : page.content()) {
            String delta = match.ratingDelta() > 0
                    ? "+" + match.ratingDelta()
                    : String.valueOf(match.ratingDelta());

            description.append(match.win() ? "WIN" : "LOSE")
                    .append(" **VS ")
                    .append(match.opponentDisplayName())
                    .append("** (")
                    .append(match.scoreFor())
                    .append(":")
                    .append(match.scoreAgainst())
                    .append(") | `")
                    .append(delta)
                    .append(" MMR` | ")
                    .append(formatDiscordTimestamp(match.createdAt()))
                    .append("\n");
        }

        embed.setDescription(description.toString());
        return embed.build();
    }

    public MessageEmbed leaderboard(PageDto<LeaderboardEntryDto> page) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Таблица лидеров (страница %d из %d)"
                .formatted(page.currentPage() + 1, page.totalPages()));
        embed.setColor(Color.YELLOW);

        StringBuilder description = new StringBuilder();
        for (LeaderboardEntryDto player : page.content()) {
            description.append(player.tierEmoji())
                    .append(" **#")
                    .append(player.rank())
                    .append("** ")
                    .append(player.displayName())
                    .append(" - `")
                    .append(player.rating())
                    .append(" MMR` ")
                    .append("(игры: ")
                    .append(player.gamesPlayed())
                    .append(")\n");
        }

        embed.setDescription(description.toString());
        return embed.build();
    }

    public String fullLeaderboard(List<LeaderboardEntryDto> players) {
        StringBuilder message = new StringBuilder();
        message.append("**АКТУАЛЬНЫЙ РЕЙТИНГ MKX RANKED**\n\n");

        for (LeaderboardEntryDto player : players) {
            message.append(String.format(
                    "%d. %s - %d (%d %s)%n",
                    player.rank(),
                    player.displayName(),
                    player.rating(),
                    player.gamesPlayed(),
                    getGamesWord(player.gamesPlayed())
            ));
        }

        return message.toString();
    }

    public List<String> splitMessage(String message, int maxLength) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : message.split("\n")) {
            if (current.length() + line.length() + 1 > maxLength) {
                chunks.add(current.toString());
                current.setLength(0);
            }

            current.append(line).append("\n");
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }

        return chunks;
    }

    private String getGamesWord(int games) {
        int lastTwoDigits = games % 100;

        if (lastTwoDigits >= 11 && lastTwoDigits <= 14) {
            return "игр";
        }

        return switch (games % 10) {
            case 1 -> "игра";
            case 2, 3, 4 -> "игры";
            default -> "игр";
        };
    }

    private String formatDiscordTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "не указано";
        }

        long epochSecond = dateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        return "<t:" + epochSecond + ":F> (<t:" + epochSecond + ":R>)";
    }
}
