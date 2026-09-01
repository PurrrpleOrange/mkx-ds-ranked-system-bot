package com.mkx.ranked.discord.listeners;

import com.mkx.ranked.discord.DiscordErrorMessageMapper;
import com.mkx.ranked.discord.formatter.RankedMessageFormatter;
import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.MatchHistoryEntryDto;
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

import java.util.List;

@Component
public class RankedCommandListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(RankedCommandListener.class);
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

        switch (componentId) {
            case "btn:match_history" -> handleMatchHistory(event);
            case "btn:leaderboard" -> handleLeaderboard(event);
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

    private void handleMatchHistory(ButtonInteractionEvent event) {
        try {
            List<MatchHistoryEntryDto> history = matchService.getFullMatchHistory(event.getUser().getIdLong());
            if (history.isEmpty()) {
                event.reply("У вас пока нет сыгранных матчей в текущем сезоне.")
                        .setEphemeral(true)
                        .queue();
                return;
            }
            replyChunks(event, formatter.matchHistory(history));
        } catch (BusinessException e) {
            event.reply(errorMessageMapper.toUserMessage(e)).setEphemeral(true).queue();
        } catch (Exception e) {
            log.error("BUTTON ERROR: failed to render match history", e);
            event.reply(errorMessageMapper.internalError()).setEphemeral(true).queue();
        }
    }

    private void handleLeaderboard(ButtonInteractionEvent event) {
        try {
            List<LeaderboardEntryDto> leaderboard = leaderboardService.getFullLeaderboardForActiveSeason();
            if (leaderboard.isEmpty()) {
                event.reply("Таблица лидеров пока пуста.").setEphemeral(true).queue();
                return;
            }
            replyChunks(event, formatter.leaderboard(leaderboard));
        } catch (BusinessException e) {
            event.reply(errorMessageMapper.toUserMessage(e)).setEphemeral(true).queue();
        } catch (Exception e) {
            log.error("BUTTON ERROR: failed to render leaderboard", e);
            event.reply(errorMessageMapper.internalError()).setEphemeral(true).queue();
        }
    }

    private void replyChunks(ButtonInteractionEvent event, List<String> chunks) {
        event.reply(chunks.get(0)).setEphemeral(true).queue(hook -> {
            hook.setEphemeral(true);
            for (int i = 1; i < chunks.size(); i++) {
                hook.sendMessage(chunks.get(i)).queue();
            }
        });
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
