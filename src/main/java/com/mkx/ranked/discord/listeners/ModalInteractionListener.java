package com.mkx.ranked.discord.listeners;

import com.mkx.ranked.discord.DiscordErrorMessageMapper;
import com.mkx.ranked.discord.formatter.RankedMessageFormatter;
import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.model.dto.MatchReportPreviewDto;
import com.mkx.ranked.service.MatchService;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ModalInteractionListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ModalInteractionListener.class);

    private final MatchService matchService;
    private final RankedMessageFormatter formatter;
    private final DiscordErrorMessageMapper errorMessageMapper;

    public ModalInteractionListener(
            MatchService matchService,
            RankedMessageFormatter formatter,
            DiscordErrorMessageMapper errorMessageMapper
    ) {
        this.matchService = matchService;
        this.formatter = formatter;
        this.errorMessageMapper = errorMessageMapper;
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();
        if (!modalId.startsWith("modal:report_match:")) {
            return;
        }

        Long opponentDiscordId = parseOpponentId(modalId);
        if (opponentDiscordId == null) {
            event.reply("Ошибка системы: неверный формат формы.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        ModalMapping myScoreMapping = event.getValue("my_score_input");
        ModalMapping opponentScoreMapping = event.getValue("opponent_score_input");
        if (myScoreMapping == null || opponentScoreMapping == null) {
            event.reply("Не все поля формы были заполнены.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        int myScore;
        int opponentScore;
        try {
            myScore = Integer.parseInt(myScoreMapping.getAsString().trim());
            opponentScore = Integer.parseInt(opponentScoreMapping.getAsString().trim());
        } catch (NumberFormatException e) {
            event.reply("Счет должен быть указан целыми числами.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        try {
            long reporterDiscordId = event.getUser().getIdLong();
            MatchReportPreviewDto preview = matchService.prepareMatchReport(
                    reporterDiscordId,
                    opponentDiscordId,
                    myScore,
                    opponentScore
            );

            String confirmCustomId = "confirm_match:%d:%d:%d:%d".formatted(
                    preview.reporterDiscordId(),
                    preview.opponentDiscordId(),
                    preview.reporterScore(),
                    preview.opponentScore()
            );
            String rejectCustomId = "reject_match:%d:%d".formatted(
                    preview.reporterDiscordId(),
                    preview.opponentDiscordId()
            );

            event.replyEmbeds(formatter.matchReportConfirmation(preview))
                    .setComponents(ActionRow.of(
                            Button.success(confirmCustomId, "Подтвердить"),
                            Button.danger(rejectCustomId, "Отклонить")
                    ))
                    .queue(
                            success -> log.info("MATCH REPORT: confirmation request sent for {} vs {}",
                                    preview.reporterDiscordId(), preview.opponentDiscordId()),
                            error -> log.error("MATCH REPORT ERROR: failed to send confirmation", error)
                    );
        } catch (BusinessException e) {
            event.reply(errorMessageMapper.toUserMessage(e))
                    .setEphemeral(true)
                    .queue();
        } catch (Exception e) {
            log.error("MATCH REPORT ERROR: unexpected failure", e);
            event.reply(errorMessageMapper.internalError())
                    .setEphemeral(true)
                    .queue();
        }
    }

    private Long parseOpponentId(String modalId) {
        String[] parts = modalId.split(":");
        if (parts.length != 3) {
            return null;
        }

        try {
            return Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
