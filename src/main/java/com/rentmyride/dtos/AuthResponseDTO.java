package com.rentmyride.dtos;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponseDTO {

    private String token;
    private String tokenType = "Bearer";
    private String role;
    private Long userId;
    private String name;
    private String email;
    private String message;
    private boolean success;

    // Success Response Builder
    public static AuthResponseDTO success(String token, String role,
                                          Long userId, String name, String email) {
        return AuthResponseDTO.builder()
                .token(token)
                .tokenType("Bearer")
                .role(role)
                .userId(userId)
                .name(name)
                .email(email)
                .success(true)
                .message("Login successful")
                .build();
    }

    // Failure Response Builder
    public static AuthResponseDTO failure(String message) {
        return AuthResponseDTO.builder()
                .success(false)
                .message(message)
                .build();
    }

    // Admin Login Request
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLoginRequest {
        private String username;
        private String password;
    }

    // OTP Request
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtpRequest {
        private String mobile;
        private String otp;
    }

    // API Error Response
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApiResponse {
        private boolean success;
        private String message;
        private Object data;
        // New — populated only for field-validation failures (see GlobalExceptionHandler's
        // MethodArgumentNotValidException handler), so a form can highlight the EXACT field(s)
        // that were wrong instead of just showing one combined message.
        private java.util.Map<String, String> fieldErrors;

        public static ApiResponse success(String message, Object data) {
            return ApiResponse.builder()
                    .success(true)
                    .message(message)
                    .data(data)
                    .build();
        }

        public static ApiResponse error(String message) {
            return ApiResponse.builder()
                    .success(false)
                    .message(message)
                    .build();
        }

        public static ApiResponse validationError(java.util.Map<String, String> fieldErrors) {
            // Combine into one readable sentence for every existing `err.response.data.message`
            // call site across the frontend to pick up automatically — AND keep the raw map
            // available (fieldErrors) for any form that wants to highlight individual fields.
            String combined = fieldErrors.entrySet().stream()
                    .map(e -> e.getValue())
                    .distinct()
                    .reduce((a, b) -> a + " " + b)
                    .orElse("Please check the highlighted fields.");
            return ApiResponse.builder()
                    .success(false)
                    .message(combined)
                    .fieldErrors(fieldErrors)
                    .build();
        }
    }
}
