package com.rentmyride.custom_exceptions;

public class CarNotAvailableException extends RuntimeException {
    public CarNotAvailableException(String message) { super(message); }
    public CarNotAvailableException(Long id) { super("Car is not available for booking. Car id: " + id); }
    public CarNotAvailableException() { super("Car is not available for booking."); }
}
