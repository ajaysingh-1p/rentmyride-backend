package com.rentmyride.dtos;

import com.rentmyride.entities.Car;
import jakarta.validation.constraints.*;
import lombok.*;

// Issue #31 fix: also doubles as the addCar/updateCar request body — annotations below are
// enforced via @Valid on those two endpoints (see CarController).
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarDTO {

    private Long carId;

    @NotBlank(message = "Brand is required.")
    private String brand;

    @NotBlank(message = "Model is required.")
    private String model;

    @NotNull(message = "Year is required.")
    @Min(value = 1990, message = "Year must be 1990 or later.")
    @Max(value = 2100, message = "Year must be realistic.")
    private Integer year;

    @NotBlank(message = "Registration number is required.")
    private String registrationNumber;

    private String color;
    private Car.FuelType fuelType;
    private Car.TransmissionType transmissionType;
    private Car.CarCategory carCategory;

    @NotNull(message = "Seating capacity is required.")
    @Min(value = 1, message = "Seating capacity must be at least 1.")
    @Max(value = 60, message = "Seating capacity must be realistic.")
    private Integer seatingCapacity;

    @NotNull(message = "Rent per day is required.")
    @Positive(message = "Rent per day must be a positive amount.")
    private Double rentPerDay;

    @PositiveOrZero(message = "Rate per km cannot be negative.")
    private Double ratePerKm;

    @PositiveOrZero(message = "Night charge cannot be negative.")
    private Double nightChargePerNight;

    @PositiveOrZero(message = "Mileage cannot be negative.")
    private Double mileageKmpl;

    private Car.AvailabilityStatus availabilityStatus;
    private String imageUrl;
    private String description;
}
