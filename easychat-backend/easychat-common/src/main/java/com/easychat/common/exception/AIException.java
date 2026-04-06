package com.easychat.common.exception;

public class AIException extends RuntimeException {
    public AIException() {
        super();
    }

    public AIException(String message) {
        super(message);
    }

    public AIException(String message, Throwable cause) {
        super(message, cause);
    }
}
