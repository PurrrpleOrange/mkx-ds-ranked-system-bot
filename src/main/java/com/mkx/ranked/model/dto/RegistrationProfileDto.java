package com.mkx.ranked.model.dto;

public record RegistrationProfileDto(
        long playerId,
        String displayName,
        int rating,
        int gamesPlayed
) {
}
