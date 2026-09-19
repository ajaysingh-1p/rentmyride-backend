package com.rentmyride.custom_exceptions;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String message) { super(message); }
    public PaymentNotFoundException(Long id) { super("Payment not found with id: " + id); }
    public PaymentNotFoundException() { super("Payment not found."); }
}
