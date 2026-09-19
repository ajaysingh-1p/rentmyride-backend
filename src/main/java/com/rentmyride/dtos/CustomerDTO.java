package com.rentmyride.dtos;

import com.rentmyride.entities.Customer;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDTO {

    private Long customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String mobileNumber;
    private String alternateMobile;
    private LocalDate dateOfBirth;
    private Customer.Gender gender;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String drivingLicenseNumber;
    private LocalDate drivingLicenseExpiry;
    private String aadharNumber;
    private String profileImageUrl;
    private String drivingLicenseImageUrl;
    private String aadharImageUrl;
    private Integer trustScore;
    private String referralCode;
    private Double walletBalance;
    private Customer.AccountStatus accountStatus;
    private String role;
    // Bug fix: the entity always had this, but it was never mapped through to the API response
    // — that's why Admin's customer list/detail view had no way to show when a customer
    // actually registered.
    private java.time.LocalDateTime createdAt;

    // Registration Request (password included only for registration)
    // Issue #31 fix: this used to have zero validation — a blank/missing email, a 3-character
    // "password", a malformed pincode, or any other garbage would sail straight through to the
    // database. These annotations are enforced by @Valid on the controller method (see
    // CustomerController.register()), which now rejects a bad request with a 400 before it ever
    // reaches the service layer.
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RegisterRequest {
        @NotBlank(message = "First name is required.")
        @Size(max = 50, message = "First name must be under 50 characters.")
        private String firstName;

        @NotBlank(message = "Last name is required.")
        @Size(max = 50, message = "Last name must be under 50 characters.")
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

        @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number.")
        private String alternateMobile;

        @Past(message = "Date of birth must be in the past.")
        private LocalDate dateOfBirth;

        private String gender; // MALE / FEMALE / OTHER

        @NotBlank(message = "Address is required.")
        private String address;

        @NotBlank(message = "City is required.")
        private String city;

        @NotBlank(message = "State is required.")
        private String state;

        @NotBlank(message = "Pincode is required.")
        @Pattern(regexp = "^\\d{6}$", message = "Enter a valid 6-digit pincode.")
        private String pincode;

        @NotBlank(message = "Driving license number is required.")
        private String drivingLicenseNumber;

        @Future(message = "Driving license expiry must be in the future.")
        private LocalDate drivingLicenseExpiry;

        @NotBlank(message = "Aadhar number is required.")
        @Pattern(regexp = "^\\d{12}$", message = "Enter a valid 12-digit Aadhar number.")
        private String aadharNumber;

        private String drivingLicenseImageUrl;
        private String aadharImageUrl;
        private String referredByCode; // optional — a friend's referral code entered at signup
    }

    // Login Request
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank(message = "Email or mobile number is required.")
        private String username; // email or mobile

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

    // Referral program summary shown on the customer's referral page
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReferralInfo {
        private String referralCode;
        private Double walletBalance;
        private long referredCount;
        private double bonusPerReferral;
    }
}
