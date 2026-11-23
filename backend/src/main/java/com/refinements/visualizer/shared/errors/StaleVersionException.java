package com.refinements.visualizer.shared.errors;

public class StaleVersionException extends RuntimeException {
    public StaleVersionException(String message) {
        super(message);
    }
}
