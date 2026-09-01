package com.mkx.ranked.discord.listeners;

import com.mkx.ranked.discord.formatter.RankedMessageFormatter;
import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.MatchHistoryEntryDto;
import com.mkx.ranked.model.dto.PageDto;
import com.mkx.ranked.model.dto.PlayerProfileDto;
import com.mkx.ranked.model.dto.RegistrationProfileDto;
import com.mkx.ranked.model.dto.RegistrationReviewDto;
import com.mkx.ranked.service.LeaderboardService;
import com.mkx.ranked.service.MatchService;
import com.mkx.ranked.service.PlayerService;
import com.mkx.ranked.service.RegistrationService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RankedCommandListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(RankedCommandListener.class);
    private static final int LEADERBOARD_PAGE_SIZE = 10;
    private static final int MATCH_HISTORY_PAGE_SIZE = 5;

    private final PlayerService playerService;
    private final RegistrationService registrationService;
    private final LeaderboardService leaderboardService;
    private final MatchService matchService;
    private final RankedMessageFormatter formatter;

    public RankedCommandListener(
            PlayerService playerService,
            RegistrationService registrationService,
            LeaderboardService leaderboardService,
            MatchService matchService,
            RankedMessageFormatter formatter
    ) {
        this.playerService = playerService;
        this.registrationService = registrationService;
        this.leaderboardService = leaderboardService;
        this.matchService = matchService;
        this.formatter = formatter;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("ranked")) {
            return;
        }

        long discordId = event.getUser().getIdLong();
        if (!registrationService.isRegistered(discordId)) {
            openRegistrationModal(event);
            return;
        }

        showRankedMenu(event);
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().equals("modal:register_user")) {
            return;
        }

        ModalMapping nicknameMapping = event.getValue("input:reg_nickname");
        if (nicknameMapping == null) {
            event.reply("Не указан игровой ник.").setEphemeral(true).queue();
            return;
        }

        String inputNickname = nicknameMapping.getAsString().trim();
        if (registrationService.isClaimedUsername(inputNickname)) {
            event.reply("Никнейм **" + inputNickname + "** уже привязан к другому Discord аккаунту.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        RegistrationReviewDto review = registrationService.reviewRegistration(inputNickname);
        if (review.unclaimedProfile().isPresent()) {
            RegistrationProfileDto profile = review.unclaimedProfile().get();
            Button confirmBtn = Button.success("btn:confirm_claim:" + profile.playerId(), "Да, это мой профиль");
            Button cancelBtn = Button.danger("btn:cancel_reg", "Отмена");

            event.replyEmbeds(formatter.registrationCandidate(profile))
                    .setComponents(ActionRow.of(confirmBtn, cancelBtn))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Button cancelBtn = Button.danger("btn:cancel_reg", "Отмена");

        event.reply("Профиль с ником **" + review.requestedNickname()
                        + "** не найден в базе. Проверьте игровой ник или обратитесь к администратору.")
                .setComponents(ActionRow.of(cancelBtn))
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        if (componentId.startsWith("btn:confirm_claim:")) {
            handleConfirmClaim(event, componentId);
            return;
        }

        if (componentId.startsWith("btn:confirm_new:")) {
            handleConfirmNew(event, componentId);
            return;
        }

        if (componentId.equals("btn:cancel_reg")) {
            event.editMessage("Регистрация отменена. Напишите `/ranked`, когда будете готовы.")
                    .setComponents()
                    .queue();
            return;
        }

        if (componentId.startsWith("btn:leaderboard_page:")) {
            int page = Integer.parseInt(componentId.substring("btn:leaderboard_page:".length()));
            handleLeaderboardPage(event, page, true);
            return;
        }

        if (componentId.startsWith("btn:history_page:")) {
            int page = Integer.parseInt(componentId.substring("btn:history_page:".length()));
            handleMatchHistoryPage(event, page, true);
            return;
        }

        switch (componentId) {
            case "btn:admin_send_rating" -> handleAdminSendRating(event);
            case "btn:match_history" -> handleMatchHistoryPage(event, 0, false);
            case "btn:leaderboard" -> handleLeaderboardPage(event, 0, false);
            case "btn:report_match" -> handleReportMatchButton(event);
            default -> {
            }
        }
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (!event.getComponentId().equals("select:opponent_report")) {
            return;
        }

        User opponent = event.getMentions().getUsers().get(0);

        TextInput myScoreInput = TextInput.create("my_score_input", TextInputStyle.SHORT)
                .setPlaceholder("Например: 5")
                .setRequired(true)
                .setRequiredRange(1, 1)
                .build();

        TextInput oppScoreInput = TextInput.create("opponent_score_input", TextInputStyle.SHORT)
                .setPlaceholder("Например: 2")
                .setRequired(true)
                .setRequiredRange(1, 1)
                .build();

        Modal modal = Modal.create("modal:report_match:" + opponent.getId(), "Счет против " + opponent.getName())
                .addComponents(
                        Label.of("Ваши победы (0 - 5)", myScoreInput),
                        Label.of("Победы соперника (0 - 5)", oppScoreInput)
                )
                .build();

        event.replyModal(modal).queue();
    }

    private void openRegistrationModal(SlashCommandInteractionEvent event) {
        TextInput nickInput = TextInput.create("input:reg_nickname", TextInputStyle.SHORT)
                .setPlaceholder("Ваш игровой ник")
                .setRequired(true)
                .setRequiredRange(2, 32)
                .build();

        Modal modal = Modal.create("modal:register_user", "Регистрация в MKX Ranked")
                .addComponents(Label.of("Игровой никнейм", nickInput))
                .build();

        event.replyModal(modal).queue();
    }

    private void showRankedMenu(SlashCommandInteractionEvent event) {
        try {
            PlayerProfileDto profile = playerService.getProfile(event.getUser().getIdLong());

            Button reportBtn = Button.primary("btn:report_match", "Внести результат");
            Button historyBtn = Button.secondary("btn:match_history", "История матчей");
            Button topBtn = Button.secondary("btn:leaderboard", "Топ игроков");
            Button adminRatingBtn = Button.primary("btn:admin_send_rating", "Отправить рейтинг");

            if (event.getMember() != null && event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.replyEmbeds(formatter.rankedMenu(profile))
                        .setComponents(
                                ActionRow.of(reportBtn, historyBtn, topBtn),
                                ActionRow.of(adminRatingBtn)
                        )
                        .setEphemeral(true)
                        .queue();
                return;
            }

            event.replyEmbeds(formatter.rankedMenu(profile))
                    .setComponents(ActionRow.of(reportBtn, historyBtn, topBtn))
                    .setEphemeral(true)
                    .queue();
        } catch (BusinessException e) {
            event.reply("Невозможно открыть меню: " + e.getMessage())
                    .setEphemeral(true)
                    .queue();
        } catch (Exception e) {
            log.error("DISCORD ERROR: failed to execute /ranked", e);
            event.reply("Произошла внутренняя ошибка сервера.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void handleConfirmClaim(ButtonInteractionEvent event, String componentId) {
        try {
            long playerId = Long.parseLong(componentId.substring("btn:confirm_claim:".length()));
            registrationService.claimProfile(
                    event.getUser().getIdLong(),
                    event.getUser().getName(),
                    playerId
            );

            event.editMessage("Профиль успешно привязан. Напишите `/ranked`, чтобы открыть меню.")
                    .setComponents()
                    .queue();
        } catch (BusinessException e) {
            event.editMessage("Не удалось привязать профиль: " + e.getMessage())
                    .setComponents()
                    .queue();
        }
    }

    private void handleConfirmNew(ButtonInteractionEvent event, String componentId) {
        event.editMessage("Создание новых игровых профилей через Discord отключено.")
                .setComponents()
                .queue();
    }

    private void handleAdminSendRating(ButtonInteractionEvent event) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("Кнопка доступна только администраторам.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        try {
            List<LeaderboardEntryDto> players = leaderboardService.getFullLeaderboardForActiveSeason();
            List<String> chunks = formatter.splitMessage(formatter.fullLeaderboard(players), 1900);

            event.deferEdit().queue();
            for (String chunk : chunks) {
                event.getChannel().sendMessage(chunk).queue();
            }
        } catch (BusinessException e) {
            event.reply("Не удалось отправить рейтинг: " + e.getMessage())
                    .setEphemeral(true)
                    .queue();
        } catch (Exception e) {
            log.error("ADMIN ERROR: failed to send leaderboard", e);
            event.reply("Не удалось отправить рейтинг.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void handleMatchHistoryPage(ButtonInteractionEvent event, int page, boolean isUpdate) {
        try {
            PageDto<MatchHistoryEntryDto> history = matchService.getMatchHistory(
                    event.getUser().getIdLong(),
                    page,
                    MATCH_HISTORY_PAGE_SIZE
            );

            if (history.totalItems() == 0) {
                sendOrEditText(event, isUpdate, "У вас пока нет сыгранных матчей в текущем сезоне.");
                return;
            }

            sendOrEditEmbed(
                    event,
                    isUpdate,
                    formatter.matchHistory(history),
                    historyButtons(history)
            );
        } catch (BusinessException e) {
            sendOrEditText(event, isUpdate, "Не удалось загрузить историю матчей: " + e.getMessage());
        } catch (Exception e) {
            log.error("BUTTON ERROR: failed to render match history", e);
            sendOrEditText(event, isUpdate, "Произошла ошибка при загрузке истории матчей.");
        }
    }

    private void handleLeaderboardPage(ButtonInteractionEvent event, int page, boolean isUpdate) {
        try {
            PageDto<LeaderboardEntryDto> leaderboard =
                    leaderboardService.getLeaderboardForActiveSeason(page, LEADERBOARD_PAGE_SIZE);

            if (leaderboard.totalItems() == 0) {
                sendOrEditText(event, isUpdate, "Таблица лидеров пока пуста.");
                return;
            }

            sendOrEditEmbed(event, isUpdate, formatter.leaderboard(leaderboard), leaderboardButtons(leaderboard));
        } catch (BusinessException e) {
            sendOrEditText(event, isUpdate, "Не удалось загрузить таблицу лидеров: " + e.getMessage());
        } catch (Exception e) {
            log.error("BUTTON ERROR: failed to render leaderboard", e);
            sendOrEditText(event, isUpdate, "Произошла ошибка при загрузке таблицы лидеров.");
        }
    }

    private ActionRow historyButtons(PageDto<?> page) {
        return ActionRow.of(
                Button.primary("btn:history_page:" + (page.currentPage() - 1), "Назад")
                        .withDisabled(page.isFirst()),
                Button.secondary("btn:noop", (page.currentPage() + 1) + " / " + page.totalPages())
                        .asDisabled(),
                Button.primary("btn:history_page:" + (page.currentPage() + 1), "Вперед")
                        .withDisabled(page.isLast())
        );
    }

    private ActionRow leaderboardButtons(PageDto<?> page) {
        return ActionRow.of(
                Button.primary("btn:leaderboard_page:" + (page.currentPage() - 1), "Назад")
                        .withDisabled(page.isFirst()),
                Button.secondary("btn:noop", (page.currentPage() + 1) + " / " + page.totalPages())
                        .asDisabled(),
                Button.primary("btn:leaderboard_page:" + (page.currentPage() + 1), "Вперед")
                        .withDisabled(page.isLast())
        );
    }

    private void sendOrEditText(ButtonInteractionEvent event, boolean isUpdate, String message) {
        if (isUpdate) {
            event.editMessage(message).setComponents().queue();
        } else {
            event.reply(message).setEphemeral(true).queue();
        }
    }

    private void sendOrEditEmbed(
            ButtonInteractionEvent event,
            boolean isUpdate,
            net.dv8tion.jda.api.entities.MessageEmbed embed,
            ActionRow actionRow
    ) {
        if (isUpdate) {
            event.editMessageEmbeds(embed)
                    .setComponents(actionRow)
                    .queue();
        } else {
            event.replyEmbeds(embed)
                    .setComponents(actionRow)
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void handleReportMatchButton(ButtonInteractionEvent event) {
        EntitySelectMenu opponentSelect =
                EntitySelectMenu.create("select:opponent_report", EntitySelectMenu.SelectTarget.USER)
                        .setPlaceholder("Выберите соперника")
                        .build();

        event.reply("С кем был сыгран матч?")
                .setComponents(ActionRow.of(opponentSelect))
                .setEphemeral(true)
                .queue();
    }
}
