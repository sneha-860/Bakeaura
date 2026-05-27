// src/main/java/com/bakeaura/exception/BadRequestException.java
package com.bakeaura.exception;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
