package com.rentmyride.dtos;

import com.rentmyride.entities.Driver;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverDTO {
    private Long driverId;
    private String firstName;
    private String lastName;
    private String email;
    private String mobileNumber;
    private String licenseNumber;
    private String licenseImageUrl;
    private Driver.Status status;
    private java.time.LocalDate dateOfJoining;
    private LocalDateTime createdAt;

    // Admin creates a driver account — issue #31 fix (see CustomerDTO for the general rationale).
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {
        @NotBlank(message = "First name is required.")
        private String firstName;

        @NotBlank(message = "Last name is required.")
        private String lastName;

        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        private String email;

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters.")
        private String password;

        @NotBlank(message = "Mobile number is required.")
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number.")
        private String mobileNumber;

        @NotBlank(message = "License number is required.")
        private String licenseNumber;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank(message = "Email is required.")
        private String email;

        @NotBlank(message = "Password is required.")
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangePasswordRequest {
        @NotBlank(message = "Current password is required.")
        private String currentPassword;

        @NotBlank(message = "New password is required.")
        @Size(min = 8, max = 100, message = "New password must be at least 8 characters.")
        private String newPassword;
    }

    // Driver's own trip/earnings summary — shown on their dashboard/profile
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StatsResponse {
        private long completedTrips;
        private long tripsThisMonth;
        private double totalKmDriven;
        private double kmDrivenThisMonth;
        private Double averageRating; // null if no ratings yet
        private long totalRatings;
    }
}
