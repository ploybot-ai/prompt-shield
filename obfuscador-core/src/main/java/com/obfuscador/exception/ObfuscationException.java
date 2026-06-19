package com.obfuscador.exception;

public class ObfuscationException extends RuntimeException {

    public ObfuscationException(String message) {
        super(message);
    }

    public ObfuscationException(String message, Throwable cause) {
        super(message, cause);
    }
}
