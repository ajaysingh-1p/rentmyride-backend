package com.rentmyride.custom_exceptions;

public class InvalidDateRangeException extends RuntimeException {
    public InvalidDateRangeException(String message) { super(message); }
    public InvalidDateRangeException() { super("Return date must be after pickup date."); }
}
