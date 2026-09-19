package com.rentmyride.custom_exceptions;

public class SavedAddressNotFoundException extends RuntimeException {
    public SavedAddressNotFoundException(Long id) { super("Saved address not found. Id: " + id); }
}
