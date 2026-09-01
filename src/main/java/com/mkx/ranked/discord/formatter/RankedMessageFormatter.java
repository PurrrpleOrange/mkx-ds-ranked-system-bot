package com.mkx.ranked.discord.formatter;

import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.MatchHistoryEntryDto;
import com.mkx.ranked.model.dto.MatchReportPreviewDto;
import com.mkx.ranked.model.dto.MatchResult;
import com.mkx.ranked.model.dto.PageDto;
import com.mkx.ranked.model.dto.PlayerProfileDto;
import com.mkx.ranked.model.dto.RegistrationResultDto;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.ZoneId;

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

    public MessageEmbed registrationCompleted(RegistrationResultDto result) {
        return new EmbedBuilder()
                .setTitle("Регистрация завершена")
                .setColor(Color.GREEN)
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

    private String formatDiscordTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "не указано";
        }

        long epochSecond = dateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        return "<t:" + epochSecond + ":F> (<t:" + epochSecond + ":R>)";
    }
}
