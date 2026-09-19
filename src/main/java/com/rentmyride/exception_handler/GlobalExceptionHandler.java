package com.rentmyride.exception_handler;

import com.rentmyride.custom_exceptions.*;
import com.rentmyride.dtos.AuthResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 404 Not Found Exceptions ──────────────────────────────

    @ExceptionHandler(CarNotFoundException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleCarNotFound(CarNotFoundException ex) {
        log.error("[DDT-ERROR] CarNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleCustomerNotFound(CustomerNotFoundException ex) {
        log.error("[DDT-ERROR] CustomerNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DriverNotFoundException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleDriverNotFound(DriverNotFoundException ex) {
        log.error("[DDT-ERROR] DriverNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(SelfDriveConfigNotFoundException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleSelfDriveConfigNotFound(SelfDriveConfigNotFoundException ex) {
        log.error("[DDT-ERROR] SelfDriveConfigNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleReservationNotFound(ReservationNotFoundException ex) {
        log.error("[DDT-ERROR] ReservationNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(RentalNotFoundException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleRentalNotFound(RentalNotFoundException ex) {
        log.error("[DDT-ERROR] RentalNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handlePaymentNotFound(PaymentNotFoundException ex) {
        log.error("[DDT-ERROR] PaymentNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvoiceNotFoundException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleInvoiceNotFound(InvoiceNotFoundException ex) {
        log.error("[DDT-ERROR] InvoiceNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(com.rentmyride.custom_exceptions.SavedAddressNotFoundException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleSavedAddressNotFound(
            com.rentmyride.custom_exceptions.SavedAddressNotFoundException ex) {
        log.error("[DDT-ERROR] SavedAddressNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(AdminNotFoundException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleAdminNotFound(AdminNotFoundException ex) {
        log.error("[DDT-ERROR] AdminNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    // ── 400 Bad Request Exceptions ────────────────────────────

    @ExceptionHandler(CarNotAvailableException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleCarNotAvailable(CarNotAvailableException ex) {
        log.error("[DDT-ERROR] CarNotAvailableException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DriverNotAvailableException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleDriverNotAvailable(DriverNotAvailableException ex) {
        log.error("[DDT-ERROR] DriverNotAvailableException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleInvalidDateRange(InvalidDateRangeException ex) {
        log.error("[DDT-ERROR] InvalidDateRangeException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidPricingException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleInvalidPricing(InvalidPricingException ex) {
        log.error("[DDT-ERROR] InvalidPricingException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleFileUpload(FileUploadException ex) {
        log.error("[DDT-ERROR] FileUploadException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(PromoCodeException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handlePromoCode(PromoCodeException ex) {
        log.warn("[DDT-WARN] PromoCodeException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleInvalidOtp(InvalidOtpException ex) {
        log.error("[DDT-ERROR] InvalidOtpException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handlePaymentFailed(PaymentFailedException ex) {
        log.error("[DDT-ERROR] PaymentFailedException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        log.error("[DDT-ERROR] InvalidCredentialsException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    // ── 429 Too Many Requests ─────────────────────────────────

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleTooManyRequests(TooManyRequestsException ex) {
        log.warn("[DDT-SECURITY] Rate limit hit: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    // ── 409 Conflict Exceptions ───────────────────────────────

    @ExceptionHandler(CustomerAlreadyExistsException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleCustomerExists(CustomerAlreadyExistsException ex) {
        log.error("[DDT-ERROR] CustomerAlreadyExistsException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateRegistrationException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleDuplicate(DuplicateRegistrationException ex) {
        log.error("[DDT-ERROR] DuplicateRegistrationException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    // ── 403 Forbidden ─────────────────────────────────────────

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleUnauthorized(UnauthorizedAccessException ex) {
        log.error("[DDT-ERROR] UnauthorizedAccessException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleAccessDenied(AccessDeniedException ex) {
        log.error("[DDT-ERROR] AccessDeniedException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(AuthResponseDTO.ApiResponse.error("Access denied. You don't have permission."));
    }

    // ── 422 Validation Errors ─────────────────────────────────
    // Bug fix: this used to return the raw {field: message} map directly as the response body —
    // NOT wrapped in the same {success, message, data} shape every other endpoint uses. Every
    // frontend catch block does `err.response?.data?.message` (matching that shared shape), so
    // for a validation error `.message` was always undefined and every form silently fell back to
    // a generic "Server error" toast — the specific field-level reason was computed correctly
    // here but never actually reached the user. Now returns the same ApiResponse shape, with a
    // readable combined `message` AND the raw `fieldErrors` map for per-field UI.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(field, message);
        });
        log.error("[DDT-ERROR] Validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(AuthResponseDTO.ApiResponse.validationError(errors));
    }

    // ── 409 Conflict ───────────────────────────────────────────
    // Used by SelfDriveConfigServiceImpl.create() to reject a duplicate per-category config
    // with a clear message instead of a raw DB unique-constraint violation reaching the admin.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleIllegalState(IllegalStateException ex) {
        log.warn("[DDT-WARN] IllegalStateException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(AuthResponseDTO.ApiResponse.error(ex.getMessage()));
    }

    // ── 500 Internal Server Error ─────────────────────────────
    // Bug fix: this used to return the exact same hardcoded generic message for EVERY unexpected
    // exception, with zero way to trace which specific failure a user hit — support had to go dig
    // through server logs blind. A short reference code is now included in BOTH the response
    // (so the user/admin can quote it) and the log line (so it's instantly greppable) — without
    // ever leaking the raw exception message/stack trace to the client, which could expose
    // internal details (SQL, file paths, field names) to an attacker.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AuthResponseDTO.ApiResponse> handleGeneral(Exception ex) {
        String refCode = "ERR-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        log.error("[DDT-ERROR] Unhandled Exception [{}]: {}", refCode, ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthResponseDTO.ApiResponse.error(
                        "Something went wrong on our end. Please try again — if it keeps happening, " +
                        "contact support and mention reference " + refCode + "."));
    }
}
