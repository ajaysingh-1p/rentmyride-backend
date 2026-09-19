package com.rentmyride.custom_exceptions;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) { super(message); }
    public InvalidCredentialsException() { super("Invalid credentials."); }
}
