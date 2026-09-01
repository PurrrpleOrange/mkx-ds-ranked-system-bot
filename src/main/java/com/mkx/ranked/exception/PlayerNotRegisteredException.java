package com.mkx.ranked.exception;

public class PlayerNotRegisteredException extends BusinessException {

    public PlayerNotRegisteredException(Long discordId) {
        super("Player with Discord ID " + discordId + " is not registered in the active season.");
    }
}
