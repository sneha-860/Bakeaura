// src/main/java/com/bakeaura/exception/ResourceNotFoundException.java
package com.bakeaura.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
