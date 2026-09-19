package com.rentmyride.custom_exceptions;

public class AdminNotFoundException extends RuntimeException {
    public AdminNotFoundException(String message) { super(message); }
    public AdminNotFoundException(Long id) { super("Admin not found with id: " + id); }
    public AdminNotFoundException() { super("Admin not found."); }
}
