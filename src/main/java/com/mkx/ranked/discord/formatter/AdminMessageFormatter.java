package com.mkx.ranked.discord.formatter;

import com.mkx.ranked.model.dto.AdminMatchDto;
import com.mkx.ranked.model.dto.AdminPlayerDto;
import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.SeasonDto;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
public class AdminMessageFormatter {

    private static final int DISCORD_MESSAGE_CHUNK_SIZE = 1900;

    public MessageEmbed seasonInfo(SeasonDto season) {
        return new EmbedBuilder()
                .setTitle("Сезон #" + season.seasonNumber() + " — " + season.name())
                .setColor(new Color(175, 0, 0))
                .addField("ID", String.valueOf(season.id()), true)
                .addField("Статус", season.status().name(), true)
                .addField("Начало", formatDiscordTimestamp(season.startDate()), false)
                .addField("Плановое окончание", formatDiscordTimestamp(season.plannedEndDate()), false)
                .addField("Фактическое окончание", formatDiscordTimestamp(season.endDate()), false)
                .build();
    }

    public MessageEmbed matchInfo(AdminMatchDto match) {
        return new EmbedBuilder()
                .setTitle("Матч #" + match.matchId())
                .setColor(Color.ORANGE)
                .addField("Сезон", "#" + match.seasonNumber(), true)
                .addField("Счёт", match.winnerScore() + ":" + match.loserScore(), true)
                .addField(
                        "Победитель",
                        "%s (<@%d>)\nDiscord ID: `%d`\nDelta: `%+d`".formatted(
                                match.winnerDisplayName(),
                                match.winnerDiscordId(),
                                match.winnerDiscordId(),
                                match.deltaWinner()
                        ),
                        false
                )
                .addField(
                        "Проигравший",
                        "%s (<@%d>)\nDiscord ID: `%d`\nDelta: `%+d`".formatted(
                                match.loserDisplayName(),
                                match.loserDiscordId(),
                                match.loserDiscordId(),
                                match.deltaLoser()
                        ),
                        false
                )
                .addField("Создан", formatDiscordTimestamp(match.createdAt()), false)
                .build();
    }

    public MessageEmbed playerInfo(AdminPlayerDto player) {
        return new EmbedBuilder()
                .setTitle("Игрок " + player.displayName())
                .setColor(Color.CYAN)
                .addField("Internal player ID", String.valueOf(player.playerId()), true)
                .addField("Сезон", "#" + player.seasonNumber(), true)
                .addField("Discord", "%s (<@%d>)".formatted(player.discordUsername(), player.discordId()), false)
                .addField("Discord ID", String.valueOf(player.discordId()), true)
                .addField("Рейтинг", String.valueOf(player.rating()), true)
                .addField("Игр", String.valueOf(player.gamesPlayed()), true)
                .addField("Место", "#" + player.rank(), true)
                .addField("Дивизион", player.tierEmoji() + " " + player.tierName(), true)
                .build();
    }

    public List<String> fullLeaderboard(List<LeaderboardEntryDto> players) {
        StringBuilder message = new StringBuilder("**АКТУАЛЬНЫЙ РЕЙТИНГ MKX RANKED**\n\n");
        if (players.isEmpty()) {
            message.append("Таблица лидеров пока пуста.\n");
        }

        for (LeaderboardEntryDto player : players) {
            message.append("%d. %s - %d (%d %s)%n".formatted(
                    player.rank(),
                    player.displayName(),
                    player.rating(),
                    player.gamesPlayed(),
                    getGamesWord(player.gamesPlayed())
            ));
        }

        return splitMessage(message.toString(), DISCORD_MESSAGE_CHUNK_SIZE);
    }

    private List<String> splitMessage(String message, int maxLength) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : message.split("\n")) {
            if (!current.isEmpty() && current.length() + line.length() + 1 > maxLength) {
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
