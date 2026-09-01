package com.mkx.ranked.exception;

/**
 * Base class for expected domain errors that can be shown to Discord users.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
