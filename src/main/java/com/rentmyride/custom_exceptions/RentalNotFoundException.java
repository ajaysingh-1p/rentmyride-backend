package com.rentmyride.custom_exceptions;

public class RentalNotFoundException extends RuntimeException {
    public RentalNotFoundException(String message) { super(message); }
    public RentalNotFoundException(Long id) { super("Rental not found with id: " + id); }
    public RentalNotFoundException() { super("Rental not found."); }
}
