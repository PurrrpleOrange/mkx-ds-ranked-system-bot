package com.mkx.ranked.exception;

public class MatchNotFoundException extends BusinessException {

    public MatchNotFoundException(Long matchId) {
        super("Match #" + matchId + " was not found.");
    }
}
