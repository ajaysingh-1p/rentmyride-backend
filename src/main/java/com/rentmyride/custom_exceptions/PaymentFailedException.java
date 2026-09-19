package com.rentmyride.custom_exceptions;

public class PaymentFailedException extends RuntimeException {
    public PaymentFailedException(String message) { super(message); }
    public PaymentFailedException() { super("Payment processing failed."); }
}
