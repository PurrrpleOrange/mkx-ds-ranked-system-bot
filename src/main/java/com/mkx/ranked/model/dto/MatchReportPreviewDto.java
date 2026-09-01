package com.mkx.ranked.model.dto;

public record MatchReportPreviewDto(
        long reporterDiscordId,
        long opponentDiscordId,
        String reporterDisplayName,
        String opponentDisplayName,
        int reporterScore,
        int opponentScore
) {
}
