package com.rentmyride.dtos;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDTO {

    private Long adminId;
    private String name;
    private String email;
    private LocalDateTime createdAt;

    // Login Request — "username" field carries the admin's email (matches existing frontend AdminLogin form)
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        private String username;
        private String password;
    }

    // Update Profile Request
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateProfileRequest {
        private String name;
        private String email;
    }

    // Change Password Request
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;
    }
}
