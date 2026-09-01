package com.mkx.ranked.discord.listeners;

import com.mkx.ranked.discord.DiscordErrorMessageMapper;
import com.mkx.ranked.discord.formatter.RankedMessageFormatter;
import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.MatchHistoryEntryDto;
import com.mkx.ranked.model.dto.PageDto;
import com.mkx.ranked.model.dto.PlayerProfileDto;
import com.mkx.ranked.model.dto.RegistrationResultDto;
import com.mkx.ranked.service.LeaderboardService;
import com.mkx.ranked.service.MatchService;
import com.mkx.ranked.service.PlayerService;
import com.mkx.ranked.service.RegistrationService;
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
    private final DiscordErrorMessageMapper errorMessageMapper;

    public RankedCommandListener(
            PlayerService playerService,
            RegistrationService registrationService,
            LeaderboardService leaderboardService,
            MatchService matchService,
            RankedMessageFormatter formatter,
            DiscordErrorMessageMapper errorMessageMapper
    ) {
        this.playerService = playerService;
        this.registrationService = registrationService;
        this.leaderboardService = leaderboardService;
        this.matchService = matchService;
        this.formatter = formatter;
        this.errorMessageMapper = errorMessageMapper;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("ranked")) {
            return;
        }

        try {
            long discordId = event.getUser().getIdLong();
            if (!registrationService.isRegistered(discordId)) {
                openRegistrationModal(event);
                return;
            }

            showRankedMenu(event);
        } catch (BusinessException e) {
            event.reply(errorMessageMapper.toUserMessage(e)).setEphemeral(true).queue();
        } catch (Exception e) {
            log.error("DISCORD ERROR: failed to execute /ranked", e);
            event.reply(errorMessageMapper.internalError()).setEphemeral(true).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().equals("modal:register_user")) {
            return;
        }

        try {
            ModalMapping nicknameMapping = event.getValue("input:reg_nickname");
            if (nicknameMapping == null) {
                event.reply("Не указан игровой ник.").setEphemeral(true).queue();
                return;
            }

            String inputNickname = nicknameMapping.getAsString().trim();
            RegistrationResultDto result = registrationService.register(
                    event.getUser().getIdLong(),
                    event.getUser().getName(),
                    inputNickname
            );
            event.replyEmbeds(formatter.registrationCompleted(result))
                    .setEphemeral(true)
                    .queue();
        } catch (BusinessException e) {
            event.reply(errorMessageMapper.toUserMessage(e)).setEphemeral(true).queue();
        } catch (Exception e) {
            log.error("REGISTRATION ERROR: failed to register user", e);
            event.reply(errorMessageMapper.internalError()).setEphemeral(true).queue();
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        if (componentId.startsWith("btn:leaderboard_page:")) {
            Integer page = parsePage(componentId, "btn:leaderboard_page:");
            if (page == null) {
                replyStaleInteraction(event);
            } else {
                handleLeaderboardPage(event, page, true);
            }
            return;
        }

        if (componentId.startsWith("btn:history_page:")) {
            Integer page = parsePage(componentId, "btn:history_page:");
            if (page == null) {
                replyStaleInteraction(event);
            } else {
                handleMatchHistoryPage(event, page, true);
            }
            return;
        }

        switch (componentId) {
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

        if (event.getMentions().getUsers().isEmpty()) {
            event.reply("Соперник не выбран. Откройте форму матча заново.")
                    .setEphemeral(true)
                    .queue();
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
            event.replyEmbeds(formatter.rankedMenu(profile))
                    .setComponents(ActionRow.of(reportBtn, historyBtn, topBtn))
                    .setEphemeral(true)
                    .queue();
        } catch (BusinessException e) {
            event.reply(errorMessageMapper.toUserMessage(e))
                    .setEphemeral(true)
                    .queue();
        } catch (Exception e) {
            log.error("DISCORD ERROR: failed to execute /ranked", e);
            event.reply(errorMessageMapper.internalError())
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

            if (page > 0 && history.totalPages() > 0 && history.content().isEmpty()) {
                history = matchService.getMatchHistory(
                        event.getUser().getIdLong(),
                        history.totalPages() - 1,
                        MATCH_HISTORY_PAGE_SIZE
                );
            }

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
            sendOrEditText(event, isUpdate, errorMessageMapper.toUserMessage(e));
        } catch (Exception e) {
            log.error("BUTTON ERROR: failed to render match history", e);
            sendOrEditText(event, isUpdate, errorMessageMapper.internalError());
        }
    }

    private void handleLeaderboardPage(ButtonInteractionEvent event, int page, boolean isUpdate) {
        try {
            PageDto<LeaderboardEntryDto> leaderboard =
                    leaderboardService.getLeaderboardForActiveSeason(page, LEADERBOARD_PAGE_SIZE);

            if (page > 0 && leaderboard.totalPages() > 0 && leaderboard.content().isEmpty()) {
                leaderboard = leaderboardService.getLeaderboardForActiveSeason(
                        leaderboard.totalPages() - 1,
                        LEADERBOARD_PAGE_SIZE
                );
            }

            if (leaderboard.totalItems() == 0) {
                sendOrEditText(event, isUpdate, "Таблица лидеров пока пуста.");
                return;
            }

            sendOrEditEmbed(event, isUpdate, formatter.leaderboard(leaderboard), leaderboardButtons(leaderboard));
        } catch (BusinessException e) {
            sendOrEditText(event, isUpdate, errorMessageMapper.toUserMessage(e));
        } catch (Exception e) {
            log.error("BUTTON ERROR: failed to render leaderboard", e);
            sendOrEditText(event, isUpdate, errorMessageMapper.internalError());
        }
    }

    private Integer parsePage(String componentId, String prefix) {
        try {
            int page = Integer.parseInt(componentId.substring(prefix.length()));
            return page < 0 ? null : page;
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    private void replyStaleInteraction(ButtonInteractionEvent event) {
        event.reply("Эта кнопка устарела. Откройте раздел заново через `/ranked`.")
                .setEphemeral(true)
                .queue();
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
