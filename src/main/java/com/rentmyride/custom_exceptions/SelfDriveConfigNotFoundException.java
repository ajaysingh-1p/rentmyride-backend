package com.rentmyride.custom_exceptions;

public class SelfDriveConfigNotFoundException extends RuntimeException {
    public SelfDriveConfigNotFoundException(String message) {
        super(message);
    }
}
