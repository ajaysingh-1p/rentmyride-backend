package com.rentmyride.custom_exceptions;

public class InvalidOtpException extends RuntimeException {
    public InvalidOtpException(String message) { super(message); }
    public InvalidOtpException() { super("Invalid or expired OTP."); }
}
