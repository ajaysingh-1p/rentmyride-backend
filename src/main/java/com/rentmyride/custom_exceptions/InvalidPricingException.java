package com.rentmyride.custom_exceptions;

public class InvalidPricingException extends RuntimeException {
    public InvalidPricingException(String message) { super(message); }
}
