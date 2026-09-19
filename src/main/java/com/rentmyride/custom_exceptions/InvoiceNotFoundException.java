package com.rentmyride.custom_exceptions;

public class InvoiceNotFoundException extends RuntimeException {
    public InvoiceNotFoundException(String message) { super(message); }
    public InvoiceNotFoundException(Long id) { super("Invoice not found with id: " + id); }
    public InvoiceNotFoundException() { super("Invoice not found."); }
}
