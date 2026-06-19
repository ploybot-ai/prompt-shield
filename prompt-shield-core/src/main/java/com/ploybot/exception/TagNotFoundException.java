package com.ploybot.exception;

public class TagNotFoundException extends RuntimeException {

    public TagNotFoundException(String hash) {
        super("Tag not found for hash: " + hash);
    }

    public TagNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
