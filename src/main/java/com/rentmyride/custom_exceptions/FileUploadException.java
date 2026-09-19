package com.rentmyride.custom_exceptions;

// Issue #35 fix: FileUploadController used to throw PaymentFailedException for things that have
// nothing to do with payments (no file provided, file too large, wrong file type...) — reusing
// it was misleading in logs/stack traces and would have mapped file-upload problems to whatever
// HTTP status/handling PaymentFailedException carries, which was never designed for this case.
public class FileUploadException extends RuntimeException {
    public FileUploadException(String message) { super(message); }
}
