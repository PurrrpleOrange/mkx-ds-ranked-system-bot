package com.mkx.ranked.discord.listeners;

import com.mkx.ranked.discord.DiscordErrorMessageMapper;
import com.mkx.ranked.discord.formatter.RankedMessageFormatter;
import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.model.dto.MatchResult;
import com.mkx.ranked.service.MatchService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MatchConfirmationListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(MatchConfirmationListener.class);

    private final MatchService matchService;
    private final RankedMessageFormatter formatter;
    private final DiscordErrorMessageMapper errorMessageMapper;
    private final ConcurrentMap<Long, Instant> handledConfirmationMessages = new ConcurrentHashMap<>();

    private static final Duration CONFIRMATION_GUARD_TTL = Duration.ofHours(1);

    public MatchConfirmationListener(
            MatchService matchService,
            RankedMessageFormatter formatter,
            DiscordErrorMessageMapper errorMessageMapper
    ) {
        this.matchService = matchService;
        this.formatter = formatter;
        this.errorMessageMapper = errorMessageMapper;
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

        evictExpiredGuards();
        long messageId = event.getMessageIdLong();
        if (handledConfirmationMessages.putIfAbsent(messageId, Instant.now()) != null) {
            event.reply("Этот результат уже подтверждается или был обработан.")
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
            handledConfirmationMessages.remove(messageId);
            event.reply(errorMessageMapper.toUserMessage(e))
                    .setEphemeral(true)
                    .queue();
        } catch (Exception e) {
            handledConfirmationMessages.remove(messageId);
            log.error("MATCH ERROR: unexpected failure while confirming match", e);
            event.reply(errorMessageMapper.internalError())
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void handleReject(ButtonInteractionEvent event, String componentId) {
        ReportedParticipants participants = parseReportedParticipants(componentId);
        if (participants == null) {
            event.reply("Ошибка обработки кнопки.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        long reporterDiscordId = participants.reporterDiscordId();
        long opponentDiscordId = participants.opponentDiscordId();
        long clickerDiscordId = event.getUser().getIdLong();

        if (clickerDiscordId != opponentDiscordId && clickerDiscordId != reporterDiscordId) {
            event.reply("Отклонить результат могут только участники матча.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        evictExpiredGuards();
        long messageId = event.getMessageIdLong();
        if (handledConfirmationMessages.putIfAbsent(messageId, Instant.now()) != null) {
            event.reply("Этот результат уже подтверждается или был обработан.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.editMessageEmbeds(formatter.matchRejected(reporterDiscordId, opponentDiscordId, clickerDiscordId))
                .setComponents()
                .queue(
                        success -> log.info("MATCH REPORT: rejected by {}", clickerDiscordId),
                        error -> {
                            handledConfirmationMessages.remove(messageId);
                            log.error("MATCH REPORT ERROR: failed to update rejected match message", error);
                        }
                );
    }

    private ReportedParticipants parseReportedParticipants(String componentId) {
        String[] parts = componentId.split(":");
        if (parts.length != 3) {
            return null;
        }

        try {
            return new ReportedParticipants(Long.parseLong(parts[1]), Long.parseLong(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void evictExpiredGuards() {
        Instant expiresBefore = Instant.now().minus(CONFIRMATION_GUARD_TTL);
        handledConfirmationMessages.entrySet().removeIf(entry -> entry.getValue().isBefore(expiresBefore));
    }

    private ReportedMatchId parseReportedMatch(String componentId) {
        String[] parts = componentId.split(":");
        if (parts.length != 5) {
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

    private record ReportedParticipants(long reporterDiscordId, long opponentDiscordId) {
    }
}
