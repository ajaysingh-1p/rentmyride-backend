package com.rentmyride.custom_exceptions;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(String message) { super(message); }
    public CustomerNotFoundException(Long id) { super("Customer not found with id: " + id); }
    public CustomerNotFoundException() { super("Customer not found."); }
}
