package com.rentmyride.custom_exceptions;

public class CustomerAlreadyExistsException extends RuntimeException {
    public CustomerAlreadyExistsException(String message) { super(message); }
    public CustomerAlreadyExistsException() { super("Customer already exists with this email or mobile."); }
}
