package com.rentmyride.custom_exceptions;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(String message) { super(message); }
    public ReservationNotFoundException(Long id) { super("Reservation not found with id: " + id); }
    public ReservationNotFoundException() { super("Reservation not found."); }
}
