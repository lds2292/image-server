package com.ktlapha.imageserver.common.exception;

public class InvalidWidthException extends RuntimeException {

    public InvalidWidthException(int width) {
        super("Not allowed width: " + width);
    }
}
