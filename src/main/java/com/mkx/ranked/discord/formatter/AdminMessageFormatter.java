package com.mkx.ranked.discord.formatter;

import com.mkx.ranked.model.dto.AdminMatchDto;
import com.mkx.ranked.model.dto.AdminPlayerDto;
import com.mkx.ranked.model.dto.AdminRegisteredPlayerDto;
import com.mkx.ranked.model.dto.AdminSeasonStatisticsDto;
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

    private static final Color INFORMATION_COLOR = new Color(0, 255, 200);
    private static final int EMBED_DESCRIPTION_LIMIT = 3800;
    private static final int DISCORD_MESSAGE_CHUNK_SIZE = 1900;

    public MessageEmbed adminMenu() {
        return new EmbedBuilder()
                .setTitle("Панель администратора MKX Ranked")
                .setColor(INFORMATION_COLOR)
                .setDescription("""
                        Выберите раздел административной панели.

                        **Опубликовать рейтинг** — отправить полный рейтинг в текущий канал.
                        **Управление сезонами** — lifecycle, список, статистика и даты.
                        **Управление матчами** — безопасный откат ошибочного матча.
                        **Управление игроками** — статистика игрока ACTIVE сезона.
                        """)
                .build();
    }

    public MessageEmbed seasonManagementMenu() {
        return new EmbedBuilder()
                .setTitle("Управление сезонами")
                .setColor(INFORMATION_COLOR)
                .setDescription("""
                        **Список сезонов** — номера, ID и статусы всех сезонов.
                        **Создать сезон** — задать номер и создать новый сезон в статусе CREATED.
                        **Активировать сезон** — выполнить CREATED → ACTIVE и опубликовать объявление.
                        **Завершить сезон** — завершить ACTIVE сезон, сохранить места и опубликовать объявление.
                        **Посмотреть статистику сезона** — результаты завершённого сезона по ID или номеру.
                        **Изменить информацию о сезоне** — изменить номер, название и плановую дату окончания ACTIVE сезона.
                        """)
                .build();
    }

    public MessageEmbed matchManagementMenu() {
        return new EmbedBuilder()
                .setTitle("Управление матчами")
                .setColor(INFORMATION_COLOR)
                .setDescription("""
                        **Удалить матч** — безопасно откатить матч ACTIVE сезона по ID.

                        Операция восстановит rating и gamesPlayed обоих игроков, затем удалит запись матча. Матчи завершённых сезонов изменить нельзя.
                        
                        ID матча игрок может узнать из своей истории матчей.
                        """)
                .build();
    }

    public MessageEmbed playerManagementMenu() {
        return new EmbedBuilder()
                .setTitle("Управление игроками")
                .setColor(INFORMATION_COLOR)
                .setDescription("""
                        **Вывести всех зарегистрированных игроков** — показать всех участников ACTIVE сезона, включая игроков без матчей.
                        **Посмотреть статистику игрока** — выбрать Discord-пользователя и показать профиль текущего ACTIVE сезона: рейтинг, игры, место и дивизион.
                        """)
                .build();
    }

    public MessageEmbed seasonInfo(SeasonDto season) {
        return new EmbedBuilder()
                .setTitle("Сезон #" + season.seasonNumber() + " — " + season.name())
                .setColor(INFORMATION_COLOR)
                .addField("ID", String.valueOf(season.id()), true)
                .addField("Статус", season.status().name(), true)
                .addField("Начало", formatDiscordTimestamp(season.startDate()), false)
                .addField("Плановое окончание", formatDiscordTimestamp(season.plannedEndDate()), false)
                .addField("Фактическое окончание", formatDiscordTimestamp(season.endDate()), false)
                .build();
    }

    public MessageEmbed seasonActivatedAnnouncement(SeasonDto season) {
        return new EmbedBuilder()
                .setTitle("Новый рейтинговый сезон начался!")
                .setColor(INFORMATION_COLOR)
                .setDescription("""
                        **Сезон #%d — %s** теперь активен.

                        Регистрация выполняется заново для каждого сезона. Откройте `/ranked`, зарегистрируйте игровой ник и присоединяйтесь к матчам.

                        **Плановое окончание:** %s
                        """.formatted(
                        season.seasonNumber(),
                        season.name(),
                        formatDiscordTimestamp(season.plannedEndDate())
                ))
                .setFooter("Удачи в новом рейтинговом сезоне!")
                .build();
    }

    public MessageEmbed seasonFinishedAnnouncement(SeasonDto season) {
        return new EmbedBuilder()
                .setTitle("Рейтинговый сезон завершён")
                .setColor(INFORMATION_COLOR)
                .setDescription("""
                        **Сезон #%d — %s** завершён.

                        Итоговые места участников сохранены. Регистрация матчей этого сезона закрыта — ожидайте объявления о начале следующего сезона.

                        **Дата завершения:** %s
                        """.formatted(
                        season.seasonNumber(),
                        season.name(),
                        formatDiscordTimestamp(season.endDate())
                ))
                .build();
    }

    public MessageEmbed previousSeasonStatistics(AdminSeasonStatisticsDto statistics) {
        SeasonDto season = statistics.season();
        String top;
        if (statistics.topPlayers().isEmpty()) {
            top = "В сезоне не было игроков со сыгранными матчами.";
        } else {
            top = leaderboardTable(null, null, statistics.topPlayers(), EMBED_DESCRIPTION_LIMIT).get(0);
        }

        return new EmbedBuilder()
                .setTitle("Статистика сезона #" + season.seasonNumber())
                .setColor(INFORMATION_COLOR)
                .setDescription("**%s**\n\n**Топ-10 сезона**\n%s".formatted(season.name(), top))
                .addField("ID", String.valueOf(season.id()), true)
                .addField("Игроков", String.valueOf(statistics.playerCount()), true)
                .addField("Матчей", String.valueOf(statistics.matchCount()), true)
                .addField("Средний рейтинг", String.valueOf(statistics.averageRating()), true)
                .addField("Начало", formatDiscordTimestamp(season.startDate()), false)
                .addField("Окончание", formatDiscordTimestamp(season.endDate()), false)
                .build();
    }

    public MessageEmbed matchInfo(AdminMatchDto match) {
        return new EmbedBuilder()
                .setTitle("Матч #" + match.matchId())
                .setColor(INFORMATION_COLOR)
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
        String rank = player.rank() == null ? "Без ранга" : "#" + player.rank();
        return new EmbedBuilder()
                .setTitle("Игрок " + player.displayName())
                .setColor(INFORMATION_COLOR)
                .addField("Internal player ID", String.valueOf(player.playerId()), true)
                .addField("Сезон", "#" + player.seasonNumber(), true)
                .addField("Discord", "%s (<@%d>)".formatted(player.discordUsername(), player.discordId()), false)
                .addField("Discord ID", String.valueOf(player.discordId()), true)
                .addField("Рейтинг", String.valueOf(player.rating()), true)
                .addField("Игр", String.valueOf(player.gamesPlayed()), true)
                .addField("Место", rank, true)
                .addField("Дивизион", player.tierEmoji() + " " + player.tierName(), true)
                .build();
    }

    public List<MessageEmbed> seasonList(List<SeasonDto> seasons) {
        List<String> descriptions;
        if (seasons.isEmpty()) {
            descriptions = List.of("Сезоны пока не созданы.");
        } else {
            List<DiscordTableFormatter.Column> columns = List.of(
                    DiscordTableFormatter.Column.right("ID"),
                    DiscordTableFormatter.Column.right("№"),
                    DiscordTableFormatter.Column.left("СТАТУС"),
                    DiscordTableFormatter.Column.left("НАЗВАНИЕ")
            );
            List<List<String>> rows = seasons.stream()
                    .map(season -> List.of(
                            String.valueOf(season.id()),
                            String.valueOf(season.seasonNumber()),
                            season.status().name(),
                            season.name()
                    ))
                    .toList();
            descriptions = DiscordTableFormatter.render(
                    null,
                    null,
                    columns,
                    rows,
                    EMBED_DESCRIPTION_LIMIT
            );
        }

        List<MessageEmbed> embeds = new ArrayList<>();
        for (int i = 0; i < descriptions.size(); i++) {
            String title = descriptions.size() == 1
                    ? "Все рейтинговые сезоны"
                    : "Все рейтинговые сезоны — %d/%d".formatted(i + 1, descriptions.size());
            embeds.add(new EmbedBuilder()
                    .setTitle(title)
                    .setColor(INFORMATION_COLOR)
                    .setDescription(descriptions.get(i))
                    .build());
        }
        return embeds;
    }

    public List<String> fullLeaderboard(int seasonNumber, List<LeaderboardEntryDto> players) {
        String heading = "**Актуальный рейтинг** " + seasonNumber + " сезона";
        if (players.isEmpty()) {
            return List.of(heading + "\n\nТаблица лидеров пока пуста.");
        }

        List<String> lines = new ArrayList<>();
        for (LeaderboardEntryDto player : players) {
            lines.add("%d. *%s* - %d (%d %s)".formatted(
                    player.rank(),
                    player.displayName(),
                    player.rating(),
                    player.gamesPlayed(),
                    getGamesWord(player.gamesPlayed())
            ));
        }
        return splitLeaderboard(heading, lines);
    }

    private List<String> splitLeaderboard(String heading, List<String> lines) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder(heading).append("\n\n");
        int linesInCurrentChunk = 0;
        for (String line : lines) {
            if (linesInCurrentChunk > 0
                    && current.length() + line.length() + 1 > DISCORD_MESSAGE_CHUNK_SIZE) {
                chunks.add(current.toString().stripTrailing());
                current = new StringBuilder(heading).append(" — продолжение\n\n");
                linesInCurrentChunk = 0;
            }
            current.append(line).append('\n');
            linesInCurrentChunk++;
        }
        chunks.add(current.toString().stripTrailing());
        return chunks;
    }

    public List<String> registeredPlayers(List<AdminRegisteredPlayerDto> players) {
        if (players.isEmpty()) {
            return List.of("""
                    **ЗАРЕГИСТРИРОВАННЫЕ ИГРОКИ ACTIVE-СЕЗОНА**

                    Всего: **0**

                    В текущем сезоне пока никто не зарегистрирован.
                    """);
        }

        List<List<String>> rows = players.stream()
                .map(player -> List.of(
                        String.valueOf(player.seasonPlayerId()),
                        player.displayName(),
                        "@" + player.discordUsername(),
                        String.valueOf(player.rating()),
                        String.valueOf(player.gamesPlayed())
                ))
                .toList();
        List<DiscordTableFormatter.Column> columns = List.of(
                DiscordTableFormatter.Column.right("ID"),
                DiscordTableFormatter.Column.left("НИК"),
                DiscordTableFormatter.Column.left("DISCORD"),
                DiscordTableFormatter.Column.right("MMR"),
                DiscordTableFormatter.Column.right("ИГРЫ")
        );
        return DiscordTableFormatter.render(
                "ЗАРЕГИСТРИРОВАННЫЕ ИГРОКИ ACTIVE-СЕЗОНА",
                "Всего: **" + players.size() + "**",
                columns,
                rows,
                DISCORD_MESSAGE_CHUNK_SIZE
        );
    }

    private List<String> leaderboardTable(
            String heading,
            String intro,
            List<LeaderboardEntryDto> players,
            int maxLength
    ) {
        List<DiscordTableFormatter.Column> columns = List.of(
                DiscordTableFormatter.Column.right("#"),
                DiscordTableFormatter.Column.left("НИК"),
                DiscordTableFormatter.Column.right("MMR"),
                DiscordTableFormatter.Column.right("ИГРЫ"),
                DiscordTableFormatter.Column.left("ДИВИЗИОН")
        );
        List<List<String>> rows = players.stream()
                .map(player -> List.of(
                        String.valueOf(player.rank()),
                        player.displayName(),
                        String.valueOf(player.rating()),
                        String.valueOf(player.gamesPlayed()),
                        player.tierName()
                ))
                .toList();
        return DiscordTableFormatter.render(heading, intro, columns, rows, maxLength);
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
