package com.mkx.ranked.discord.listeners;

import com.mkx.ranked.discord.DiscordErrorMessageMapper;
import com.mkx.ranked.discord.formatter.AdminMessageFormatter;
import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.model.dto.AdminMatchDto;
import com.mkx.ranked.model.dto.AdminPlayerDto;
import com.mkx.ranked.model.dto.AdminSeasonStatisticsDto;
import com.mkx.ranked.model.dto.SeasonDto;
import com.mkx.ranked.service.AdminService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Component
public class AdminCommandListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(AdminCommandListener.class);

    private static final String BUTTON_PREFIX = "admin:button:";
    private static final String MODAL_PREFIX = "admin:modal:";
    private static final String PLAYER_SELECT_ID = "admin:select:player_info";

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
        if (!ensureAdminAccess(event, event)) {
            return;
        }

        executeAdminAction(event, "open menu", () -> event.replyEmbeds(formatter.adminMenu())
                .setComponents(rootAdminMenuRows())
                .setEphemeral(true)
                .queue());
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith(BUTTON_PREFIX)) {
            return;
        }
        if (!ensureAdminAccess(event, event)) {
            return;
        }

        executeAdminAction(event, "button " + event.getComponentId(), () -> dispatchButton(event));
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith(MODAL_PREFIX)) {
            return;
        }
        if (!ensureAdminAccess(event, event)) {
            return;
        }

        executeAdminAction(event, "modal " + event.getModalId(), () -> dispatchModal(event));
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (!PLAYER_SELECT_ID.equals(event.getComponentId())) {
            return;
        }
        if (!ensureAdminAccess(event, event)) {
            return;
        }

        executeAdminAction(event, "player info select", () -> showSelectedPlayer(event));
    }

    private void dispatchButton(ButtonInteractionEvent event) {
        switch (event.getComponentId()) {
            case "admin:button:manage_seasons" -> event.replyEmbeds(formatter.seasonManagementMenu())
                    .setComponents(seasonManagementRows())
                    .setEphemeral(true)
                    .queue();
            case "admin:button:manage_matches" -> event.replyEmbeds(formatter.matchManagementMenu())
                    .setComponents(matchManagementRows())
                    .setEphemeral(true)
                    .queue();
            case "admin:button:manage_players" -> event.replyEmbeds(formatter.playerManagementMenu())
                    .setComponents(playerManagementRows())
                    .setEphemeral(true)
                    .queue();
            case "admin:button:season_create" -> event.replyModal(seasonCreateModal()).queue();
            case "admin:button:season_activate" -> event.replyModal(seasonActivateModal()).queue();
            case "admin:button:season_finish" -> finishSeason(event);
            case "admin:button:season_info" -> event.replyModal(seasonInfoModal()).queue();
            case "admin:button:season_list" -> showSeasonList(event);
            case "admin:button:season_update", "admin:button:season_planned_end" -> openSeasonUpdateModal(event);
            case "admin:button:season_statistics" -> event.replyModal(seasonStatisticsModal()).queue();
            case "admin:button:match_info" -> event.replyModal(matchModal("info")).queue();
            case "admin:button:match_delete" -> event.replyModal(matchModal("delete")).queue();
            case "admin:button:player_info" -> openPlayerSelect(event);
            case "admin:button:player_list" -> showRegisteredPlayers(event);
            case "admin:button:leaderboard_publish" -> publishLeaderboard(event);
            default -> event.reply("Эта административная кнопка устарела. Откройте `/admin` заново.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void dispatchModal(ModalInteractionEvent event) {
        switch (event.getModalId()) {
            case "admin:modal:season_create" -> createSeason(event);
            case "admin:modal:season_activate" -> activateSeason(event);
            case "admin:modal:season_info" -> showSeasonInfo(event);
            case "admin:modal:season_update" -> updateSeasonInfo(event);
            case "admin:modal:season_planned_end" -> updatePlannedEndDate(event);
            case "admin:modal:season_statistics" -> showPreviousSeasonStatistics(event);
            case "admin:modal:match_info" -> showMatchInfo(event);
            case "admin:modal:match_delete" -> deleteMatch(event);
            default -> event.reply("Эта административная форма устарела. Откройте `/admin` заново.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private List<ActionRow> rootAdminMenuRows() {
        return List.of(ActionRow.of(
                Button.primary("admin:button:leaderboard_publish", "Опубликовать рейтинг"),
                Button.secondary("admin:button:manage_seasons", "Управление сезонами"),
                Button.secondary("admin:button:manage_matches", "Управление матчами"),
                Button.secondary("admin:button:manage_players", "Управление игроками")
        ));
    }

    private List<ActionRow> seasonManagementRows() {
        return List.of(
                ActionRow.of(
                        Button.secondary("admin:button:season_list", "Список сезонов"),
                        Button.primary("admin:button:season_create", "Создать сезон"),
                        Button.success("admin:button:season_activate", "Активировать сезон"),
                        Button.danger("admin:button:season_finish", "Завершить сезон"),
                        Button.secondary("admin:button:season_statistics", "Посмотреть статистику сезона")
                ),
                ActionRow.of(
                        Button.secondary("admin:button:season_update", "Изменить информацию о сезоне")
                )
        );
    }

    private List<ActionRow> matchManagementRows() {
        return List.of(ActionRow.of(
                Button.danger("admin:button:match_delete", "Удалить матч")
        ));
    }

    private List<ActionRow> playerManagementRows() {
        return List.of(ActionRow.of(
                Button.secondary("admin:button:player_list", "Вывести всех зарегистрированных игроков"),
                Button.secondary("admin:button:player_info", "Посмотреть статистику игрока")
        ));
    }

    private Modal seasonCreateModal() {
        TextInput name = TextInput.create("season_name", TextInputStyle.SHORT)
                .setPlaceholder("Например: Winter Clash")
                .setRequired(true)
                .setRequiredRange(1, 100)
                .build();
        TextInput plannedEnd = TextInput.create("planned_end", TextInputStyle.SHORT)
                .setPlaceholder("Например: 2026-12-01T20:00")
                .setRequired(false)
                .setMaxLength(32)
                .build();

        return Modal.create("admin:modal:season_create", "Создание сезона")
                .addComponents(
                        Label.of("Название", name),
                        Label.of("Плановое окончание (необязательно)", plannedEnd)
                )
                .build();
    }

    private Modal seasonActivateModal() {
        TextInput number = numericInput("season_number", "Например: 9", 10);
        return Modal.create("admin:modal:season_activate", "Активация сезона")
                .addComponents(Label.of("Номер сезона", number))
                .build();
    }

    private Modal seasonInfoModal() {
        TextInput seasonId = TextInput.create("season_id", TextInputStyle.SHORT)
                .setPlaceholder("Внутренний ID из списка сезонов")
                .setRequired(false)
                .setMaxLength(20)
                .build();
        TextInput number = TextInput.create("season_number", TextInputStyle.SHORT)
                .setPlaceholder("Номер сезона; оба поля пустые — ACTIVE")
                .setRequired(false)
                .setMaxLength(10)
                .build();
        return Modal.create("admin:modal:season_info", "Информация о сезоне")
                .addComponents(
                        Label.of("ID сезона (необязательно)", seasonId),
                        Label.of("Номер сезона (необязательно)", number)
                )
                .build();
    }

    private Modal matchModal(String operation) {
        TextInput matchId = numericInput("match_id", "Например: 501", 20);
        boolean delete = "delete".equals(operation);
        return Modal.create(
                        "admin:modal:match_" + operation,
                        delete ? "Откат и удаление матча" : "Информация о матче"
                )
                .addComponents(Label.of("ID матча", matchId))
                .build();
    }

    private Modal seasonPlannedEndModal() {
        TextInput plannedEnd = TextInput.create("planned_end", TextInputStyle.SHORT)
                .setPlaceholder("Например: 2026-12-01T20:00")
                .setRequired(true)
                .setRequiredRange(1, 32)
                .build();
        return Modal.create("admin:modal:season_planned_end", "Плановое окончание ACTIVE сезона")
                .addComponents(Label.of("Новая дата в ISO-формате", plannedEnd))
                .build();
    }

    private Modal seasonUpdateModal(SeasonDto season) {
        TextInput name = TextInput.create("season_name", TextInputStyle.SHORT)
                .setValue(season.name())
                .setRequired(true)
                .setRequiredRange(1, 100)
                .build();
        var plannedEndBuilder = TextInput.create("planned_end", TextInputStyle.SHORT)
                .setPlaceholder("Например: 2026-12-01T20:00")
                .setRequired(false)
                .setMaxLength(32);
        if (season.plannedEndDate() != null) {
            plannedEndBuilder.setValue(season.plannedEndDate().toString());
        }

        return Modal.create("admin:modal:season_update", "Изменение ACTIVE сезона")
                .addComponents(
                        Label.of("Название", name),
                        Label.of("Плановое окончание (пусто — убрать)", plannedEndBuilder.build())
                )
                .build();
    }

    private Modal seasonStatisticsModal() {
        TextInput seasonId = TextInput.create("season_id", TextInputStyle.SHORT)
                .setPlaceholder("Внутренний ID из списка сезонов")
                .setRequired(false)
                .setMaxLength(20)
                .build();
        TextInput number = TextInput.create("season_number", TextInputStyle.SHORT)
                .setPlaceholder("Либо номер завершённого сезона")
                .setRequired(false)
                .setMaxLength(10)
                .build();
        return Modal.create("admin:modal:season_statistics", "Статистика предыдущего сезона")
                .addComponents(
                        Label.of("ID сезона (необязательно)", seasonId),
                        Label.of("Номер сезона (необязательно)", number)
                )
                .build();
    }

    private TextInput numericInput(String id, String placeholder, int maxLength) {
        return TextInput.create(id, TextInputStyle.SHORT)
                .setPlaceholder(placeholder)
                .setRequired(true)
                .setRequiredRange(1, maxLength)
                .build();
    }

    private void createSeason(ModalInteractionEvent event) {
        String name = requireModalValue(event, "season_name");
        String plannedEndValue = optionalModalValue(event, "planned_end");
        LocalDateTime plannedEnd = plannedEndValue == null ? null : parseDateTime(plannedEndValue);
        SeasonDto season = adminService.createSeason(name, plannedEnd);
        event.replyEmbeds(formatter.seasonInfo(season)).setEphemeral(true).queue();
    }

    private void activateSeason(ModalInteractionEvent event) {
        int seasonNumber = parsePositiveInt(requireModalValue(event, "season_number"), "Номер сезона");
        SeasonDto season = adminService.activateSeason(seasonNumber);
        event.replyEmbeds(formatter.seasonActivatedAnnouncement(season)).setEphemeral(false).queue();
    }

    private void finishSeason(IReplyCallback event) {
        SeasonDto season = adminService.finishActiveSeason();
        event.replyEmbeds(formatter.seasonFinishedAnnouncement(season)).setEphemeral(false).queue();
    }

    private void showSeasonInfo(ModalInteractionEvent event) {
        String idValue = optionalModalValue(event, "season_id");
        String numberValue = optionalModalValue(event, "season_number");
        if (idValue != null && numberValue != null) {
            throw new IllegalArgumentException("Укажите либо ID сезона, либо номер, но не оба значения.");
        }

        SeasonDto season;
        if (idValue != null) {
            season = adminService.getSeasonInfoById(parsePositiveLong(idValue, "ID сезона"));
        } else {
            Integer seasonNumber = numberValue == null
                    ? null
                    : parsePositiveInt(numberValue, "Номер сезона");
            season = adminService.getSeasonInfo(seasonNumber);
        }
        event.replyEmbeds(formatter.seasonInfo(season)).setEphemeral(true).queue();
    }

    private void showSeasonList(IReplyCallback event) {
        replyEmbedList(event, formatter.seasonList(adminService.getAllSeasons()), true);
    }

    private void openSeasonUpdateModal(ButtonInteractionEvent event) {
        SeasonDto season = adminService.getSeasonInfo(null);
        event.replyModal(seasonUpdateModal(season)).queue();
    }

    private void updateSeasonInfo(ModalInteractionEvent event) {
        String name = requireModalValue(event, "season_name");
        String plannedEndValue = optionalModalValue(event, "planned_end");
        LocalDateTime plannedEnd = plannedEndValue == null ? null : parseDateTime(plannedEndValue);
        SeasonDto season = adminService.updateActiveSeasonInfo(name, plannedEnd);
        event.replyEmbeds(formatter.seasonInfo(season)).setEphemeral(true).queue();
    }

    private void updatePlannedEndDate(ModalInteractionEvent event) {
        LocalDateTime plannedEnd = parseDateTime(requireModalValue(event, "planned_end"));
        SeasonDto season = adminService.updateActiveSeasonPlannedEndDate(plannedEnd);
        event.replyEmbeds(formatter.seasonInfo(season)).setEphemeral(true).queue();
    }

    private void showPreviousSeasonStatistics(ModalInteractionEvent event) {
        String idValue = optionalModalValue(event, "season_id");
        String numberValue = optionalModalValue(event, "season_number");
        if (idValue == null && numberValue == null) {
            throw new IllegalArgumentException("Укажите ID или номер завершённого сезона.");
        }
        if (idValue != null && numberValue != null) {
            throw new IllegalArgumentException("Укажите либо ID сезона, либо номер, но не оба значения.");
        }

        AdminSeasonStatisticsDto statistics = idValue != null
                ? adminService.getPreviousSeasonStatisticsById(parsePositiveLong(idValue, "ID сезона"))
                : adminService.getPreviousSeasonStatisticsByNumber(
                        parsePositiveInt(numberValue, "Номер сезона")
                );
        event.replyEmbeds(formatter.previousSeasonStatistics(statistics))
                .setEphemeral(true)
                .queue();
    }

    private void showMatchInfo(ModalInteractionEvent event) {
        long matchId = parsePositiveLong(requireModalValue(event, "match_id"), "ID матча");
        AdminMatchDto match = adminService.getMatchInfo(matchId);
        event.replyEmbeds(formatter.matchInfo(match)).setEphemeral(true).queue();
    }

    private void deleteMatch(ModalInteractionEvent event) {
        long matchId = parsePositiveLong(requireModalValue(event, "match_id"), "ID матча");
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

    private void openPlayerSelect(ButtonInteractionEvent event) {
        EntitySelectMenu playerSelect = EntitySelectMenu.create(
                        PLAYER_SELECT_ID,
                        EntitySelectMenu.SelectTarget.USER
                )
                .setPlaceholder("Выберите Discord-пользователя")
                .build();
        event.reply("Выберите игрока текущего ACTIVE сезона.")
                .setComponents(ActionRow.of(playerSelect))
                .setEphemeral(true)
                .queue();
    }

    private void showSelectedPlayer(EntitySelectInteractionEvent event) {
        if (event.getMentions().getUsers().isEmpty()) {
            throw new IllegalArgumentException("Discord-пользователь не выбран.");
        }
        User user = event.getMentions().getUsers().get(0);
        AdminPlayerDto player = adminService.getPlayerInfo(user.getIdLong());
        event.replyEmbeds(formatter.playerInfo(player)).setEphemeral(true).queue();
    }

    private void showRegisteredPlayers(IReplyCallback event) {
        List<String> chunks = formatter.registeredPlayers(adminService.getAllRegisteredPlayers());
        replyMessageChunks(event, chunks, true);
    }

    private void publishLeaderboard(IReplyCallback event) {
        List<String> chunks = formatter.fullLeaderboard(adminService.getActiveSeasonLeaderboard());
        replyMessageChunks(event, chunks, false);
    }

    private void replyMessageChunks(IReplyCallback event, List<String> chunks, boolean ephemeral) {
        event.reply(chunks.get(0)).setEphemeral(ephemeral).queue(hook -> {
            hook.setEphemeral(ephemeral);
            for (int i = 1; i < chunks.size(); i++) {
                hook.sendMessage(chunks.get(i)).queue();
            }
        });
    }

    private void replyEmbedList(IReplyCallback event, List<MessageEmbed> embeds, boolean ephemeral) {
        int firstBatchEnd = Math.min(10, embeds.size());
        event.replyEmbeds(embeds.subList(0, firstBatchEnd)).setEphemeral(ephemeral).queue(hook -> {
            hook.setEphemeral(ephemeral);
            for (int from = firstBatchEnd; from < embeds.size(); from += 10) {
                int to = Math.min(from + 10, embeds.size());
                hook.sendMessageEmbeds(embeds.subList(from, to)).queue();
            }
        });
    }

    private boolean ensureAdminAccess(GenericInteractionCreateEvent event, IReplyCallback replyCallback) {
        if (event.getGuild() == null
                || event.getMember() == null
                || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            replyCallback.reply("Команда доступна только администраторам сервера.")
                    .setEphemeral(true)
                    .queue();
            return false;
        }
        return true;
    }

    private void executeAdminAction(IReplyCallback event, String operation, Runnable action) {
        try {
            action.run();
        } catch (BusinessException e) {
            event.reply(errorMessageMapper.toUserMessage(e)).setEphemeral(true).queue();
        } catch (IllegalArgumentException e) {
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? "Некорректные параметры административной операции."
                    : e.getMessage();
            event.reply(message).setEphemeral(true).queue();
        } catch (Exception e) {
            log.error("ADMIN DISCORD ERROR: failed to execute {}", operation, e);
            event.reply(errorMessageMapper.internalError()).setEphemeral(true).queue();
        }
    }

    private String requireModalValue(ModalInteractionEvent event, String id) {
        ModalMapping mapping = event.getValue(id);
        if (mapping == null || mapping.getAsString().isBlank()) {
            throw new IllegalArgumentException("Отсутствует обязательное поле `" + id + "`.");
        }
        return mapping.getAsString().trim();
    }

    private String optionalModalValue(ModalInteractionEvent event, String id) {
        ModalMapping mapping = event.getValue(id);
        if (mapping == null || mapping.getAsString().isBlank()) {
            return null;
        }
        return mapping.getAsString().trim();
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Некорректная дата. Используйте ISO-формат, например `2026-12-01T20:00`."
            );
        }
    }

    private long parsePositiveLong(String value, String fieldName) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException("not positive");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " должен быть положительным целым числом.");
        }
    }

    private int parsePositiveInt(String value, String fieldName) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new NumberFormatException("not positive");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " должен быть положительным 32-битным числом.");
        }
    }
}
