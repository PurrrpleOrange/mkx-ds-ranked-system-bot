package com.mkx.ranked.discord.listeners;

import com.mkx.ranked.discord.formatter.RankedMessageFormatter;
import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.model.dto.MatchResult;
import com.mkx.ranked.service.MatchService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MatchConfirmationListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(MatchConfirmationListener.class);

    private final MatchService matchService;
    private final RankedMessageFormatter formatter;

    public MatchConfirmationListener(
            MatchService matchService,
            RankedMessageFormatter formatter
    ) {
        this.matchService = matchService;
        this.formatter = formatter;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        if (componentId.startsWith("confirm_match:")) {
            handleConfirm(event, componentId);
        } else if (componentId.startsWith("reject_match:")) {
            handleReject(event, componentId);
        }
    }

    private void handleConfirm(ButtonInteractionEvent event, String componentId) {
        ReportedMatchId reportedMatch = parseReportedMatch(componentId);
        if (reportedMatch == null) {
            event.reply("Ошибка обработки кнопки.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        long clickerDiscordId = event.getUser().getIdLong();
        if (clickerDiscordId != reportedMatch.opponentDiscordId()) {
            event.reply("Подтвердить результат может только заявленный соперник <@"
                            + reportedMatch.opponentDiscordId() + ">.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        try {
            MatchResult result = matchService.confirmReportedMatch(
                    reportedMatch.reporterDiscordId(),
                    reportedMatch.opponentDiscordId(),
                    reportedMatch.reporterScore(),
                    reportedMatch.opponentScore()
            );

            event.editMessageEmbeds(formatter.matchRegistered(result, clickerDiscordId))
                    .setComponents()
                    .queue(
                            success -> log.info("MATCH SUCCESS: match #{} persisted", result.matchId()),
                            error -> log.error("MATCH ERROR: failed to update confirmation message", error)
                    );
        } catch (BusinessException e) {
            event.reply("Ошибка записи матча: " + e.getMessage())
                    .setEphemeral(true)
                    .queue();
        } catch (Exception e) {
            log.error("MATCH ERROR: unexpected failure while confirming match", e);
            event.reply("Произошла внутренняя ошибка сервера при сохранении результата.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void handleReject(ButtonInteractionEvent event, String componentId) {
        String[] parts = componentId.split(":");
        if (parts.length < 3) {
            event.reply("Ошибка обработки кнопки.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        long reporterDiscordId = Long.parseLong(parts[1]);
        long opponentDiscordId = Long.parseLong(parts[2]);
        long clickerDiscordId = event.getUser().getIdLong();

        if (clickerDiscordId != opponentDiscordId && clickerDiscordId != reporterDiscordId) {
            event.reply("Отклонить результат могут только участники матча.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.editMessageEmbeds(formatter.matchRejected(reporterDiscordId, opponentDiscordId, clickerDiscordId))
                .setComponents()
                .queue();
    }

    private ReportedMatchId parseReportedMatch(String componentId) {
        String[] parts = componentId.split(":");
        if (parts.length < 5) {
            return null;
        }

        try {
            return new ReportedMatchId(
                    Long.parseLong(parts[1]),
                    Long.parseLong(parts[2]),
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4])
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record ReportedMatchId(
            long reporterDiscordId,
            long opponentDiscordId,
            int reporterScore,
            int opponentScore
    ) {
    }
}
