package com.rentmyride.custom_exceptions;

public class DriverNotFoundException extends RuntimeException {
    public DriverNotFoundException(String message) { super(message); }
    public DriverNotFoundException(Long id) { super("Driver not found with id: " + id); }
    public DriverNotFoundException() { super("Driver not found."); }
}
