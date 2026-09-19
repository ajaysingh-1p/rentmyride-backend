package com.rentmyride.dtos;

import com.rentmyride.entities.Car;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelfDriveConfigDTO {

    private Long configId;

    @NotNull(message = "Car category is required.")
    private Car.CarCategory carCategory;

    @NotNull(message = "Security deposit is required.")
    @PositiveOrZero(message = "Security deposit cannot be negative.")
    private Double securityDeposit;

    @NotNull(message = "Free km per day is required.")
    @PositiveOrZero(message = "Free km per day cannot be negative.")
    private Double freeKmPerDay;

    @PositiveOrZero(message = "Overage rate cannot be negative.")
    private Double overageRatePerKm;

    @NotNull(message = "Late return penalty is required.")
    @PositiveOrZero(message = "Late return penalty cannot be negative.")
    private Double lateReturnPenaltyPerHour;

    @NotNull(message = "Refund window is required.")
    @Positive(message = "Refund window must be at least 1 day.")
    private Integer refundWindowDays;

    private boolean enabled;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
