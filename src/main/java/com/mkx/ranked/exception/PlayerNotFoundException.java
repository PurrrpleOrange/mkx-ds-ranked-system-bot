package com.mkx.ranked.exception;

public class PlayerNotFoundException extends BusinessException {

    public PlayerNotFoundException(Long discordId) {
        super("Player with Discord ID " + discordId + " was not found.");
    }
}
