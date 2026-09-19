package com.rentmyride.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

public class OtpDTO {

    // Step 1: request an OTP to be sent (login OTP or forgot-password OTP)
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendRequest {
        @NotBlank(message = "Email or mobile number is required.")
        private String identifier; // email (or mobile) of the customer
    }

    // Step 2: verify the OTP that was sent
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerifyRequest {
        @NotBlank(message = "Identifier is required.")
        private String identifier;

        @NotBlank(message = "OTP is required.")
        @Size(min = 4, max = 8, message = "Enter a valid OTP.")
        private String otp;
    }

    // Step 3 (forgot password only): reset the password after OTP is verified
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResetPasswordRequest {
        @NotBlank(message = "Identifier is required.")
        private String identifier;

        @NotBlank(message = "OTP is required.")
        private String otp;

        @NotBlank(message = "New password is required.")
        @Size(min = 8, max = 100, message = "New password must be at least 8 characters.")
        private String newPassword;
    }
}
