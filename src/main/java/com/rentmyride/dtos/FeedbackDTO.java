package com.rentmyride.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

public class FeedbackDTO {

    // Customer submits this after a completed rental
    // Issue #32 fix: every rating field previously had no bound at all — a value like 999 or -50
    // could be submitted and would silently corrupt any average computed from it. All five
    // ratings are now clamped to the intended 1–5 scale (driverRating stays optional/nullable
    // since not every rental has an assigned driver, but IS bounds-checked when present).
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmitRequest {
        @NotNull(message = "Rental id is required.")
        private Long rentalId;

        @NotNull(message = "Car condition rating is required.")
        @Min(value = 1, message = "Rating must be between 1 and 5.")
        @Max(value = 5, message = "Rating must be between 1 and 5.")
        private Integer carCondition;

        @NotNull(message = "Staff behavior rating is required.")
        @Min(value = 1, message = "Rating must be between 1 and 5.")
        @Max(value = 5, message = "Rating must be between 1 and 5.")
        private Integer staffBehavior;

        @NotNull(message = "Value for money rating is required.")
        @Min(value = 1, message = "Rating must be between 1 and 5.")
        @Max(value = 5, message = "Rating must be between 1 and 5.")
        private Integer valueForMoney;

        @NotNull(message = "Booking process rating is required.")
        @Min(value = 1, message = "Rating must be between 1 and 5.")
        @Max(value = 5, message = "Rating must be between 1 and 5.")
        private Integer bookingProcess;

        @NotNull(message = "Overall service rating is required.")
        @Min(value = 1, message = "Rating must be between 1 and 5.")
        @Max(value = 5, message = "Rating must be between 1 and 5.")
        private Integer overallService;

        @Min(value = 1, message = "Rating must be between 1 and 5.")
        @Max(value = 5, message = "Rating must be between 1 and 5.")
        private Integer driverRating; // optional — only if the rental had an assigned driver

        private String comments;
    }

    // Returned to admin (list view) and to the customer (confirmation)
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long feedbackId;
        private Long rentalId;
        private Long customerId;
        private String customerName;
        private String carLabel; // e.g. "Maruti Swift"
        private Integer carCondition;
        private Integer staffBehavior;
        private Integer valueForMoney;
        private Integer bookingProcess;
        private Integer overallService;
        private Integer driverRating;
        private Double averageRating;
        private String comments;
        private LocalDateTime createdAt;
    }

    // Shown to customers browsing/viewing a car — aggregate rating, not tied to any one review
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CarRatingSummary {
        private Long carId;
        private Double averageRating;
        private Long totalReviews;
    }
}
