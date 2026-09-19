package com.rentmyride.dtos;

import com.rentmyride.entities.PromoCode;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromoCodeDTO {
    private Long promoId;

    @NotBlank(message = "Promo code is required.")
    private String code;

    private String description;
    private PromoCode.DiscountType discountType;

    @Positive(message = "Discount value must be positive.")
    private Double discountValue;

    @PositiveOrZero(message = "Max discount amount cannot be negative.")
    private Double maxDiscountAmount;

    @PositiveOrZero(message = "Minimum booking amount cannot be negative.")
    private Double minBookingAmount;

    private LocalDate validFrom;
    private LocalDate validUntil;

    @Positive(message = "Usage limit must be positive.")
    private Integer usageLimit;

    private Integer usedCount;
    private boolean active;
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidateRequest {
        @NotBlank(message = "Promo code is required.")
        private String code;

        @NotNull(message = "Booking amount is required.")
        @Positive(message = "Booking amount must be positive.")
        private Double bookingAmount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ValidateResponse {
        private boolean valid;
        private String message;
        private Double discountAmount;
        private Double finalAmount;
    }
}
