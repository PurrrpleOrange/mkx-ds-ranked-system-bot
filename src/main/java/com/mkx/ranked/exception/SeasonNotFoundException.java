package com.mkx.ranked.exception;

public class SeasonNotFoundException extends BusinessException {

    public SeasonNotFoundException(Integer seasonNumber) {
        super("Season #" + seasonNumber + " was not found.");
    }

    public SeasonNotFoundException(Long seasonId) {
        super("Season with ID " + seasonId + " was not found.");
    }
}
