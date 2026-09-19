package com.rentmyride.custom_exceptions;

public class DuplicateRegistrationException extends RuntimeException {
    public DuplicateRegistrationException(String message) { super(message); }
    public DuplicateRegistrationException() { super("Duplicate registration detected."); }
}
