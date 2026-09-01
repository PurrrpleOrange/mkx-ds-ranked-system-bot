package com.mkx.ranked.model.dto;

import java.util.Optional;

public record RegistrationReviewDto(
        String requestedNickname,
        Optional<RegistrationProfileDto> unclaimedProfile
) {
}
