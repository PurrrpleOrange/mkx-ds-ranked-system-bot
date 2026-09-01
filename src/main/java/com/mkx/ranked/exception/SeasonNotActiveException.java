package com.mkx.ranked.exception;

public class SeasonNotActiveException extends BusinessException {

    public SeasonNotActiveException() {
        super("There is no active ranked season.");
    }
}
