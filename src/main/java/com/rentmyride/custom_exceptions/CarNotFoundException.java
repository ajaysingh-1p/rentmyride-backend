package com.rentmyride.custom_exceptions;

public class CarNotFoundException extends RuntimeException {
    public CarNotFoundException(String message) { super(message); }
    public CarNotFoundException(Long id) { super("Car not found with id: " + id); }
    public CarNotFoundException() { super("Car not found."); }
}
