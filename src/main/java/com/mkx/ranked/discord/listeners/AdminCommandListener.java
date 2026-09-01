package com.mkx.ranked.discord.listeners;

import com.mkx.ranked.discord.DiscordErrorMessageMapper;
import com.mkx.ranked.discord.formatter.AdminMessageFormatter;
import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.model.dto.AdminMatchDto;
import com.mkx.ranked.model.dto.AdminPlayerDto;
import com.mkx.ranked.model.dto.SeasonDto;
import com.mkx.ranked.service.AdminService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Component
public class AdminCommandListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(AdminCommandListener.class);

    private final AdminService adminService;
    private final AdminMessageFormatter formatter;
    private final DiscordErrorMessageMapper errorMessageMapper;

    public AdminCommandListener(
            AdminService adminService,
            AdminMessageFormatter formatter,
            DiscordErrorMessageMapper errorMessageMapper
    ) {
        this.adminService = adminService;
        this.formatter = formatter;
        this.errorMessageMapper = errorMessageMapper;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"admin".equalsIgnoreCase(event.getName())) {
            return;
        }

        if (!event.isFromGuild()
                || event.getMember() == null
                || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("Команда доступна только администраторам сервера.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        try {
            dispatch(event);
        } catch (BusinessException e) {
            event.reply(errorMessageMapper.toUserMessage(e)).setEphemeral(true).queue();
        } catch (IllegalArgumentException e) {
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? "Некорректные параметры административной команды."
                    : e.getMessage();
            event.reply(message).setEphemeral(true).queue();
        } catch (Exception e) {
            log.error("ADMIN DISCORD ERROR: failed to execute /admin", e);
            event.reply(errorMessageMapper.internalError()).setEphemeral(true).queue();
        }
    }

    private void dispatch(SlashCommandInteractionEvent event) {
        String group = event.getSubcommandGroup();
        String subcommand = event.getSubcommandName();
        if (group == null || subcommand == null) {
            throw new IllegalArgumentException("Не указана административная операция.");
        }

        switch (group + ":" + subcommand) {
            case "season:create" -> createSeason(event);
            case "season:activate" -> activateSeason(event);
            case "season:finish" -> finishSeason(event);
            case "season:info" -> showSeasonInfo(event);
            case "match:info" -> showMatchInfo(event);
            case "match:delete" -> deleteMatch(event);
            case "player:info" -> showPlayerInfo(event);
            case "leaderboard:publish" -> publishLeaderboard(event);
            default -> throw new IllegalArgumentException("Неизвестная административная операция.");
        }
    }

    private void createSeason(SlashCommandInteractionEvent event) {
        String name = requireOption(event, "name").getAsString();
        OptionMapping plannedEndOption = event.getOption("planned_end");
        LocalDateTime plannedEnd = plannedEndOption == null
                ? null
                : parseDateTime(plannedEndOption.getAsString());
        SeasonDto season = adminService.createSeason(name, plannedEnd);
        event.replyEmbeds(formatter.seasonInfo(season)).setEphemeral(true).queue();
    }

    private void activateSeason(SlashCommandInteractionEvent event) {
        int seasonNumber = parsePositiveInt(requireOption(event, "number"), "Номер сезона");
        SeasonDto season = adminService.activateSeason(seasonNumber);
        event.reply("Сезон #%d активирован.".formatted(season.seasonNumber()))
                .setEphemeral(true)
                .queue();
    }

    private void finishSeason(SlashCommandInteractionEvent event) {
        SeasonDto season = adminService.finishActiveSeason();
        event.reply("Сезон #%d завершён, итоговые места сохранены.".formatted(season.seasonNumber()))
                .setEphemeral(true)
                .queue();
    }

    private void showSeasonInfo(SlashCommandInteractionEvent event) {
        OptionMapping numberOption = event.getOption("number");
        Integer seasonNumber = numberOption == null ? null : parsePositiveInt(numberOption, "Номер сезона");
        SeasonDto season = adminService.getSeasonInfo(seasonNumber);
        event.replyEmbeds(formatter.seasonInfo(season)).setEphemeral(true).queue();
    }

    private void showMatchInfo(SlashCommandInteractionEvent event) {
        long matchId = parsePositiveLong(requireOption(event, "id").getAsString(), "ID матча");
        AdminMatchDto match = adminService.getMatchInfo(matchId);
        event.replyEmbeds(formatter.matchInfo(match)).setEphemeral(true).queue();
    }

    private void deleteMatch(SlashCommandInteractionEvent event) {
        long matchId = parsePositiveLong(requireOption(event, "id").getAsString(), "ID матча");
        AdminMatchDto match = adminService.getMatchInfo(matchId);
        adminService.deleteMatch(matchId);
        event.reply("Матч #%d (%s %d:%d %s) удалён, рейтинг и gamesPlayed восстановлены."
                        .formatted(
                                match.matchId(),
                                match.winnerDisplayName(),
                                match.winnerScore(),
                                match.loserScore(),
                                match.loserDisplayName()
                        ))
                .setEphemeral(true)
                .queue();
    }

    private void showPlayerInfo(SlashCommandInteractionEvent event) {
        User user = requireOption(event, "user").getAsUser();
        AdminPlayerDto player = adminService.getPlayerInfo(user.getIdLong());
        event.replyEmbeds(formatter.playerInfo(player)).setEphemeral(true).queue();
    }

    private void publishLeaderboard(SlashCommandInteractionEvent event) {
        List<String> chunks = formatter.fullLeaderboard(adminService.getActiveSeasonLeaderboard());
        event.reply(chunks.get(0)).setEphemeral(false).queue(hook -> {
            for (int i = 1; i < chunks.size(); i++) {
                hook.sendMessage(chunks.get(i)).queue();
            }
        });
    }

    private OptionMapping requireOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping option = event.getOption(name);
        if (option == null) {
            throw new IllegalArgumentException("Отсутствует обязательный параметр `" + name + "`.");
        }
        return option;
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Некорректный `planned_end`. Используйте ISO-формат, например `2026-12-01T20:00`."
            );
        }
    }

    private long parsePositiveLong(String value, String fieldName) {
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed <= 0) {
                throw new NumberFormatException("not positive");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " должен быть положительным целым числом.");
        }
    }

    private int parsePositiveInt(OptionMapping option, String fieldName) {
        try {
            int parsed = Math.toIntExact(option.getAsLong());
            if (parsed <= 0) {
                throw new ArithmeticException("not positive");
            }
            return parsed;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(fieldName + " должен быть положительным 32-битным числом.");
        }
    }
}
