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
import java.util.ArrayList;
import java.util.List;

@Component
public class RankedMessageFormatter {

    private static final Color INFORMATION_COLOR = new Color(0, 255, 200);
    private static final int DISCORD_MESSAGE_CHUNK_SIZE = 1900;

    public MessageEmbed rankedMenu(PlayerProfileDto profile) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Mortal Kombat X - Ranked Season #" + profile.season().seasonNumber());
        embed.setColor(INFORMATION_COLOR);
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
        StringBuilder message = new StringBuilder("**ИСТОРИЯ МАТЧЕЙ ТЕКУЩЕГО СЕЗОНА**\n\n");
        for (MatchHistoryEntryDto match : matches) {
            String delta = match.ratingDelta() > 0
                    ? "+" + match.ratingDelta()
                    : String.valueOf(match.ratingDelta());

            message.append("`Матч #")
                    .append(match.matchId())
                    .append("` | ")
                    .append(match.win() ? "WIN" : "LOSE")
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
        return splitMessage(message.toString());
    }

    public List<String> leaderboard(List<LeaderboardEntryDto> players) {
        StringBuilder message = new StringBuilder("**ТАБЛИЦА ЛИДЕРОВ ТЕКУЩЕГО СЕЗОНА**\n\n");
        for (LeaderboardEntryDto player : players) {
            message.append(player.tierEmoji())
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
        return splitMessage(message.toString());
    }

    private List<String> splitMessage(String message) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : message.split("\n")) {
            if (!current.isEmpty()
                    && current.length() + line.length() + 1 > DISCORD_MESSAGE_CHUNK_SIZE) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            current.append(line).append('\n');
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private String formatDiscordTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "не указано";
        }

        long epochSecond = dateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        return "<t:" + epochSecond + ":F> (<t:" + epochSecond + ":R>)";
    }
}
